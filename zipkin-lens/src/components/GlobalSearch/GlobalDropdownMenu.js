import React from 'react';
import Modal from 'react-modal';

class GlobalDropdownMenu extends React.Component {
  constructor(props) {
    super(props);
    this.state = { isModalOpened: false };
    this.handleOpenModalToggle = this.handleOpenModalToggle.bind(this);
  }

  handleOpenModalToggle() {
    const { isModalOpened } = this.state;
    this.setState({ isModalOpened: !isModalOpened });
  }

  render() {
    const { isModalOpened } = this.state;

    return (
      <div className="global-dropdown-menu">
        <div
          className="global-dropdown-menu__button"
          onClick={this.handleOpenModalToggle}
          role="presentation"
        >
          <i className="fas fa-bars" />
        </div>
        <div
          className="global-dropdown-menu__modal-wrapper"
        >
          <Modal
            className="global-dropdown-menu__modal"
            overlayClassName="global-dropdown-menu__overlay"
            isOpen={isModalOpened}
            parentSelector={() => document.querySelector('.global-dropdown-menu__modal-wrapper')}
          >
            <div className="global-dropdown-menu__trace-id">
              <div className="global-dropdown-menu__trace-id-label">Trace ID</div>
              <input
                className="global-dropdown-menu__trace-id-input"
                type="text"
              />
            </div>
            <div className="global-dropdown-menu__menu">
              Open JSON
            </div>
          </Modal>
        </div>
      </div>
    );
  }
}

export default GlobalDropdownMenu;
