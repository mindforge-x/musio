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
  const [message, setMessage] = useState("给我推荐 5 首适合深夜写代码听的歌。");

  async function startChat(event: FormEvent) {
    event.preventDefault();
    if (!message.trim()) {
      return;
    }

    onBusyChange(true);
    try {
      const run = await chatClient.startChat(message);
      onEvent({ id: crypto.randomUUID(), name: "run", detail: `已创建任务：${run.runId}` });
      chatClient.openRunEvents(run.runId, {
        onMessage: (detail) => onEvent({ id: crypto.randomUUID(), name: "agent", detail }),
        onToolStart: (detail) => onEvent({ id: crypto.randomUUID(), name: "tool_start", detail }),
        onToolResult: (detail) => onEvent({ id: crypto.randomUUID(), name: "tool_result", detail }),
        onSongCards: (songs) => {
          onSongs(songs);
          onEvent({ id: crypto.randomUUID(), name: "song_cards", detail: `收到 ${songs.length} 首歌曲` });
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
        detail: error instanceof Error ? error.message : "未知错误"
      });
    }
  }

  return (
    <section className="panel command-panel">
      <div className="panel-heading">
        <h2>Agent 对话</h2>
        <span>{busy ? "运行中" : "空闲"}</span>
      </div>
      <form onSubmit={startChat} className="prompt-form">
        <textarea value={message} onChange={(event) => setMessage(event.target.value)} />
        <button type="submit" disabled={busy || !message.trim()}>
          <Play size={18} />
          发送
        </button>
      </form>
    </section>
  );
}
