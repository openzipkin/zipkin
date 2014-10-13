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

  subject { middleware(app) }

  it 'traces a request' do
    expect(::Trace).to receive(:push).ordered
    expect(::Trace).to receive(:set_rpc_name).ordered
    expect(::Trace).to receive(:pop).ordered
    expect(::Trace).to receive(:record).exactly(3).times
    status, headers, body = subject.call(mock_env())

    # return expected status
    expect(status).to eq(200)
  end

  context 'with sample rate set to 0' do
    before(:each) { ::Trace.sample_rate = 0 }

    it 'does not sample a request' do
      expect(::Trace).to receive(:push) do |trace_id|
        expect(trace_id.sampled?).to be_falsy
      end
      status, headers, body = subject.call(mock_env())

      # return expected status
      expect(status).to eq(200)
    end

    it 'always samples if debug flag is passed in header' do
      expect(::Trace).to receive(:push) do |trace_id|
        expect(trace_id.sampled?).to be_truthy
      end
      status, headers, body = subject.call(
        mock_env('/', 'HTTP_X_B3_FLAGS' => ::Trace::Flags::DEBUG.to_s))

      # return expected status
      expect(status).to eq(200)
    end
  end
end
