import './HenkiloViewContactContent.css'
import React from 'react'
import Columns from 'react-columns'
import Field from 'field';
import Button from "button";
import {updateHenkilo} from "../../external/henkiloClient";

const HenkiloViewContactContent = React.createClass({
    propTypes: {
        l10n: React.PropTypes.object.isRequired,
        henkilo: React.PropTypes.object.isRequired,
        readOnly: React.PropTypes.bool.isRequired,
        locale: React.PropTypes.string.isRequired,
    },
    getInitialState: function() {
        this.henkiloUpdate = this.props.henkilo.result;

        return {
            readOnly: this.props.readOnly,
            showPassive: false,
            contactInfo: this.henkiloUpdate.yhteystiedotRyhma.map((yhteystiedotRyhma, idx) =>
                yhteystiedotRyhma.yhteystieto.map((yhteystieto, idx2) =>
                    ({translation: yhteystieto.yhteystietoTyyppi, value: yhteystieto.yhteystietoArvo,
                            inputValue: 'yhteystiedotRyhma.' + idx + '.yhteystieto.' + idx2 + '.yhteystietoArvo'}
                    )
            )),
        }
    },
    render: function() {
        const L = this.props.l10n;
        return (
            <div className="henkiloViewUserContentWrapper">
                <Columns columns={1}>
                    <div>
                        <div className="header">
                            <h2>{L['HENKILO_YHTEYSTIEDOT_OTSIKKO']}</h2>
                        </div>
                        <div className="henkiloViewContent">
                            {this.state.contactInfo.map((yhteystiedotRyhma, idx) =>
                            <div key={idx}>
                                <Columns columns={this.state.contactInfo.length}>
                                    <h3>ryhmanimi</h3>
                                    { yhteystiedotRyhma.map((yhteystieto, idx2) =>
                                        <div key={idx2} id={yhteystieto.translation}>
                                            { !this.state.readOnly || yhteystieto.value
                                                ? <Columns columns={2}>
                                                    <span className="strong">{L[yhteystieto.translation]}</span>
                                                    <Field inputValue={yhteystieto.inputValue} changeAction={this._updateModelField}
                                                           readOnly={this.state.readOnly}>{yhteystieto.value}</Field>
                                                </Columns>
                                                : null}

                                        </div>
                                    ) }
                                </Columns>
                            </div>
                            )}
                        </div>
                    </div>
                </Columns>
                {this.state.readOnly
                    ? <div className="henkiloViewButtons">
                        <Button big action={this._edit}>{L['MUOKKAA_LINKKI']}</Button>
                    </div>
                    : <div className="henkiloViewEditButtons">
                        <Button big action={this._discard}>{L['PERUUTA_LINKKI']}</Button>
                        <Button confirm big action={this._update}>{L['TALLENNA_LINKKI']}</Button>
                    </div>
                }
            </div>
        )
    },
    _edit: function () {
        this.setState({readOnly: false});
        this._preEditData = {
            basicInfo: this.state.basicInfo,
            contactInfo: this.state.contactInfo,
            organisationInfo: this.state.organisationInfo,
        }
    },
    _discard: function () {
        this.setState({
            readOnly: true,
            basicInfo: this._preEditData.basicInfo,
            contactInfo: this._preEditData.contactInfo,
            organisationInfo: this._preEditData.organisationInfo,
        });
    },
    _update: function () {
        updateHenkilo(this.henkiloUpdate);
    },
    _updateModelField: function (event) {
        const value = event.target.value;
        const fieldpath = event.target.name;
        this._updateFieldByDotAnnotation(this.henkiloUpdate, fieldpath, value);
    },
    _updateFieldByDotAnnotation: function(obj, path, value) {
        let schema = obj;  // a moving reference to internal objects within obj
        const pList = path.split('.');
        const len = pList.length;
        for(let i = 0; i < len-1; i++) {
            let elem = pList[i];
            if( !schema[elem] ) {
                schema[elem] = {};
            }
            schema = schema[elem];
        }

        schema[pList[len-1]] = value;
    }
});

export default HenkiloViewContactContent
