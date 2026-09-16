import { Toaster } from '@sbb-polarion/react-sbb-polarion';
import ReportView from './components/ReportView';
import { findFeature } from './features';

/**
 * Top-level feature router. One index.html / bundle; the page is chosen by the `feature` query
 * parameter. Two callers set it from outside this app: `hivemodule.xml` opens `?feature=about`, and
 * the Live Report widget embeds `?feature=report`. A URL with no (or an unknown) feature falls back
 * to the report, which is what the widget showed before the parameter existed.
 */
export default function App() {
  const feature = new URLSearchParams(window.location.search).get('feature');
  const match = findFeature(feature);
  const Page = match ? match.component : ReportView;

  return (
    // `.app` supplies the page shell (RSP's PageLayout.css) and `standard-admin-page` the --sbb-*
    // control tokens and Polarion-styled controls. The `feature-<id>` class lets one page opt into a
    // layout the others must not get - here the report, which renders inside the widget's iframe and
    // must not carry the administration page's padding.
    <div className={`app standard-admin-page${match ? ` feature-${match.id}` : ' feature-report'}`}>
      <Toaster />
      <Page />
    </div>
  );
}
