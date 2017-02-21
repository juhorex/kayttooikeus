'use strict';

Object.defineProperty(exports, "__esModule", {
    value: true
});

var _react = require('react');

var _react2 = _interopRequireDefault(_react);

var _select = require('select');

var _select2 = _interopRequireDefault(_select);

require('./Field.css');

var _bind = require('classnames/bind');

var _bind2 = _interopRequireDefault(_bind);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

var Field = _react2.default.createClass({
    displayName: 'Field',

    propTypes: {
        readOnly: _react2.default.PropTypes.bool,
        changeAction: _react2.default.PropTypes.func,
        inputValue: _react2.default.PropTypes.string,
        selectValue: _react2.default.PropTypes.string
    },
    getInitialState: function getInitialState() {
        return {
            readOnly: true
        };
    },
    render: function render() {
        var className = (0, _bind2.default)({ 'field': true,
            '${this.props.className}': this.props.className,
            'readonly': this.props.readOnly });
        return this.props.readOnly ? _react2.default.createElement(
            'span',
            { className: className },
            this.props.children
        ) : this.props.data ? _react2.default.createElement(_select2.default, { data: this.props.data, name: this.props.inputValue, onSelect: this.props.changeAction,
            value: this.props.selectValue }) : _react2.default.createElement('input', { className: className, name: this.props.inputValue, onChange: this.props.changeAction,
            defaultValue: this.props.children });
    }
});
exports.default = Field;
