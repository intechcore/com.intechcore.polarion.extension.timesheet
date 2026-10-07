import { useState } from 'react';
import type { ExportOptions } from '../utils/exportPdf';

/**
 * Polarion's own PDF export icon, served by the platform: the one of its document toolbar, and of the
 * PDF Exporter's button.
 */
export const PDF_EXPORT_ICON = '/polarion/ria/images/dle/operations/actionPdfExport16.svg';

interface Props extends ExportOptions {
  disabled?: boolean;
}

export default function ExportPdfButton({ disabled, ...options }: Props) {
  const [busy, setBusy] = useState(false);

  const onClick = async () => {
    setBusy(true);
    try {
      // Loaded on demand - keeps jsPDF out of the initial bundle.
      const { exportTimesheetPdf } = await import('../utils/exportPdf');
      await exportTimesheetPdf(options);
    } catch (e) {
      console.error(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    // sbb-btn--control is RSP's toolbar button (Polarion's .polarion-generalToolbarButton), the one
    // that belongs in a row of controls: fixed height, unlike the dialog button, which grows with its
    // content. export-pdf-button carries no styling, it only names the button.
    <button
      type="button"
      className="sbb-btn sbb-btn--control export-pdf-button"
      disabled={disabled || busy}
      onClick={onClick}
    >
      {/* Decorative: the text names the action. RSP's icon class sizes it and spaces it off the text. */}
      <img className="sbb-btn__icon" src={PDF_EXPORT_ICON} alt="" />
      {/* An element, not bare text: RSP spaces the icon only when it is not the only child. */}
      <span>{busy ? 'Generating…' : 'Export PDF'}</span>
    </button>
  );
}
