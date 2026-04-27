import { FormEvent, useState } from "react";
import { Play } from "lucide-react";
import { EventLog, Song } from "../../shared/types";
import { chatClient } from "./chatClient";

type AgentChatPanelProps = {
  busy: boolean;
  onBusyChange: (busy: boolean) => void;
  onEvent: (event: EventLog) => void;
  onSongs: (songs: Song[]) => void;
};

export function AgentChatPanel({ busy, onBusyChange, onEvent, onSongs }: AgentChatPanelProps) {
  const [message, setMessage] = useState("Recommend five songs for a late-night coding session.");

  async function startChat(event: FormEvent) {
    event.preventDefault();
    if (!message.trim()) {
      return;
    }

    onBusyChange(true);
    try {
      const run = await chatClient.startChat(message);
      onEvent({ id: crypto.randomUUID(), name: "run", detail: `${run.state}: ${run.runId}` });
      chatClient.openRunEvents(run.runId, {
        onMessage: (detail) => onEvent({ id: crypto.randomUUID(), name: "agent", detail }),
        onToolStart: (detail) => onEvent({ id: crypto.randomUUID(), name: "tool_start", detail }),
        onToolResult: (detail) => onEvent({ id: crypto.randomUUID(), name: "tool_result", detail }),
        onSongCards: (songs) => {
          onSongs(songs);
          onEvent({ id: crypto.randomUUID(), name: "song_cards", detail: `received ${songs.length} songs` });
        },
        onError: (detail) => {
          onEvent({ id: crypto.randomUUID(), name: "agent_error", detail });
          onBusyChange(false);
        },
        onDone: () => onBusyChange(false)
      });
    } catch (error) {
      onBusyChange(false);
      onEvent({
        id: crypto.randomUUID(),
        name: "error",
        detail: error instanceof Error ? error.message : "Unknown error"
      });
    }
  }

  return (
    <section className="panel command-panel">
      <div className="panel-heading">
        <h2>Agent Run</h2>
        <span>{busy ? "running" : "idle"}</span>
      </div>
      <form onSubmit={startChat} className="prompt-form">
        <textarea value={message} onChange={(event) => setMessage(event.target.value)} />
        <button type="submit" disabled={busy || !message.trim()}>
          <Play size={18} />
          Run
        </button>
      </form>
    </section>
  );
}
