import { CircleStop } from "lucide-react";
import { EventLog } from "../../shared/types";

type AgentEventsProps = {
  events: EventLog[];
  onClear: () => void;
};

export function AgentEvents({ events, onClear }: AgentEventsProps) {
  return (
    <section className="panel event-panel">
      <div className="panel-heading">
        <h2>Events</h2>
        <button type="button" onClick={onClear} aria-label="Clear events">
          <CircleStop size={17} />
        </button>
      </div>
      <div className="event-list">
        {events.map((item) => (
          <article key={item.id} className="event-row">
            <span>{item.name}</span>
            <p>{item.detail}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
