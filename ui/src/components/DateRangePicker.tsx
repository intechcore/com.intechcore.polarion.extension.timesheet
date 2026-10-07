interface Props {
  startDate: string;
  endDate: string;
  onStartChange: (value: string) => void;
  onEndChange: (value: string) => void;
  disabled?: boolean;
}

export default function DateRangePicker({ startDate, endDate, onStartChange, onEndChange, disabled }: Props) {
  return (
    <>
      <div className="control">
        <span>From</span>
        <input
          type="date"
          value={startDate}
          max={endDate || undefined}
          disabled={disabled}
          onChange={(e) => onStartChange(e.target.value)}
        />
      </div>
      <div className="control">
        <span>To</span>
        <input
          type="date"
          value={endDate}
          min={startDate || undefined}
          disabled={disabled}
          onChange={(e) => onEndChange(e.target.value)}
        />
      </div>
    </>
  );
}
