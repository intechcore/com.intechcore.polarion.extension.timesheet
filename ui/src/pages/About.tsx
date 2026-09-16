import { About as RspAbout } from '@sbb-polarion/react-sbb-polarion';
import appIcon from '../assets/app-icon.svg';
import useRemote from '../services/useRemote';

/**
 * The standard extension About page, from react-sbb-polarion. It replaces a hand-written copy of
 * generic's about.jsp that lived here (AdminView): the same manifest table and configuration
 * properties, plus the README help article and the REST-token test the shared page adds.
 */
export default function About() {
  const { sendRequest } = useRemote();
  return <RspAbout sendRequest={sendRequest} appIcon={appIcon} restApiUrl="/polarion/timesheet/rest/api/version" />;
}
