require 'rack/mock'
require 'spec_helper'

describe ZipkinTracer::RackHandler do
  def middleware(app, config={})
    ZipkinTracer::RackHandler.new(app, config)
  end

  def mock_env(path = '/', params = {})
    @request = Rack::MockRequest.env_for(path, params)
  end

  let(:app) {
    lambda { |env|
      [200, { 'Content-Type' => 'text/plain' }, ['hello']]
    }
  }

  # stub ip address lookup
  let(:host_ip) { 0x11223344 }
  before(:each) {
    allow(::Trace::Endpoint).to receive(:host_to_i32).and_return(host_ip)
  }

  context 'configured without plugins' do
    subject { middleware(app) }

    it 'traces a request' do
      expect(::Trace).to receive(:push).ordered
      expect(::Trace).to receive(:set_rpc_name).ordered
      expect(::Trace).to receive(:pop).ordered
      expect(::Trace).to receive(:record).exactly(3).times
      status, headers, body = subject.call(mock_env)

      # return expected status
      expect(status).to eq(200)
      expect { |b| body.each &b }.to yield_with_args('hello')
    end
  end

  context 'configured with annotation plugin' do
    let(:annotate) do
      lambda do |env, status, response_headers, response_body|
        # string annotation
        ::Trace.record(::Trace::BinaryAnnotation.new('foo', env['foo'] || 'FOO', 'STRING', ::Trace.default_endpoint))
        # integer annotation
        ::Trace.record(::Trace::BinaryAnnotation.new('http.status', [status.to_i].pack('n'), 'I16', ::Trace.default_endpoint))
      end
    end
    subject { middleware(app, :annotate_plugin => annotate) }

    it 'traces a request with additional annotations' do
      expect(::Trace).to receive(:push).ordered
      expect(::Trace).to receive(:set_rpc_name).ordered
      expect(::Trace).to receive(:pop).ordered
      expect(::Trace).to receive(:record).exactly(5).times
      status, headers, body = subject.call(mock_env)

      # return expected status
      expect(status).to eq(200)
    end
  end

  context 'configured with filter plugin that allows all' do
    subject { middleware(app, :filter_plugin => lambda {|env| true}) }

    it 'traces the request' do
      expect(::Trace).to receive(:push)
      status, _, _ = subject.call(mock_env)
      expect(status).to eq(200)
    end
  end

  context 'configured with filter plugin that allows none' do
    subject { middleware(app, :filter_plugin => lambda {|env| false}) }

    it 'traces the request' do
      expect(::Trace).not_to receive(:push)
      status, _, _ = subject.call(mock_env)
      expect(status).to eq(200)
    end
  end

  context 'with sample rate set to 0' do
    before(:each) { ::Trace.sample_rate = 0 }

    context 'configured with whitelist plugin that forces sampling' do
      subject { middleware(app, :whitelist_plugin => lambda {|env| true}) }

      it 'samples the request' do
        expect(::Trace).to receive(:push) do |trace_id|
          expect(trace_id.sampled?).to be_truthy
        end
        expect(::Trace).to receive(:record).exactly(4).times # extra whitelisted annotation
        status, _, _ = subject.call(mock_env)
        expect(status).to eq(200)
      end
    end

    context 'configured with filter plugin that allows none' do
      subject { middleware(app, :whitelist_plugin => lambda {|env| false}) }

      it 'does not sample the request' do
        expect(::Trace).to receive(:push) do |trace_id|
          expect(trace_id.sampled?).to be_falsey
        end
        expect(::Trace).to receive(:record).exactly(3).times # normal annotations
        status, _, _ = subject.call(mock_env)
        expect(status).to eq(200)
      end
    end
  end
end
