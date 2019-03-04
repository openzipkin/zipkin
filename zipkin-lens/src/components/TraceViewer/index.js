import React from 'react';
import Dropzone from 'react-dropzone';

import DetailedTraceSummary from '../DetailedTraceSummary';
import { treeCorrectedForClockSkew, detailedTraceSummary } from '../../zipkin';

const dropzoneBaseStyle = {
  width: 200,
  height: 60,
  fontSize: '18px',
  padding: '8px',
  borderWidth: 2,
  borderColor: '#666',
  borderStyle: 'dashed',
  borderRadius: 5,
};

const dropzoneActiveStyle = {
  borderStyle: 'solid',
  borderColor: '#6c6',
  backgroundColor: '#eee',
};

class TraceViewer extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      traceSummary: null,
      cannotOpenFile: false,
      isInvalidFormat: false,
    };
    this.handleDrop = this.handleDrop.bind(this);
    this.handleCancel = this.handleCancel.bind(this);
  }

  handleDrop(files) {
    const [file] = files;
    const fileReader = new FileReader();

    fileReader.onload = () => {
      const { result } = fileReader;
      let rawTrace;
      try {
        rawTrace = JSON.parse(result);
      } catch (error) {
        this.setState({
          traceSummary: null,
          cannotOpenFile: false,
          isInvalidFormat: true,
        });
      }
      this.setState({
        traceSummary: detailedTraceSummary(
          treeCorrectedForClockSkew(rawTrace),
        ),
        cannotOpenFile: false,
        isInvalidFormat: false,
      });
    };

    fileReader.onabort = () => {
      this.setState({
        traceSummary: null,
        cannotOpenFile: true,
        isInvalidFormat: false,
      });
    };
    fileReader.onerror = fileReader.onabort;
    fileReader.readAsText(file);
  }

  handleCancel() {
    this.setState({
      traceSummary: null,
      cannotOpenFile: false,
      isInvalidFormat: false,
    });
  }

  renderDropzone() {
    return (
      <Dropzone
        onDrop={this.handleDrop}
        onFileDialogCancel={this.handleCancel}
      >
        {({ getRootProps, getInputProps, isDragActive }) => {
          const styles = isDragActive
            ? { ...dropzoneBaseStyle, dropzoneActiveStyle }
            : { ...dropzoneBaseStyle };
          return (
            <div
              {...getRootProps()}
              style={styles}
            >
              <input {...getInputProps()} />
              <p>Choose file</p>
            </div>
          );
        }}
      </Dropzone>
    );
  }

  renderTraceSummary() {
    const { traceSummary, cannotOpenFile, isInvalidFormat } = this.state;

    if (cannotOpenFile) {
      return (
        <div className="trace-viewer__error-message">
          Cannot open the file...
        </div>
      );
    }
    if (isInvalidFormat) {
      return (
        <div className="trace-viewer__error-message">
          Invalid format...
        </div>
      );
    }
    if (!traceSummary) {
      return null;
    }
    return (
      <DetailedTraceSummary traceSummary={traceSummary} />
    );
  }

  render() {
    return (
      <div className="trace-viewer">
        <div className="trace-viewer__dropzone-wrapper">
          {this.renderDropzone()}
        </div>
        {this.renderTraceSummary()}
      </div>
    );
  }
}

export default TraceViewer;
