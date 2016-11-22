import 'oph-urls-js';
import '../../test.properties.js'

import React from 'react'
import ReactDOM from 'react-dom'

import KutsuForm from './KutsuForm'

const appState = {
  addedOrgs: [],
  basicInfo: {},
  l10n: {},
  locale: 'fi',
  orgs: {
    numHits: 1,
    organisaatiot: [
      {oid: 1, organisaatiotyypit: []},
    ]
  },
  languages: [{ code: '', name: {}}]
};

it('renders without crashing', () => {
  const div = document.createElement('div');
  ReactDOM.render(<KutsuForm {...appState} />, div)
});
