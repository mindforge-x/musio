import { FormEvent, KeyboardEvent, useState } from "react";
import { ArrowUp } from "lucide-react";
import { EventLog, Song } from "../../shared/types";
import { AgentMessageList } from "./AgentMessageList";
import { chatClient } from "./chatClient";
import { ChatMessage } from "./chatTypes";

type AgentChatPanelProps = {
  busy: boolean;
  disabledReason?: string | null;
  onBusyChange: (busy: boolean) => void;
  onEvent: (event: EventLog) => void;
  onSongs: (songs: Song[]) => void;
};

export function AgentChatPanel({ busy, disabledReason, onBusyChange, onEvent, onSongs }: AgentChatPanelProps) {
  const [message, setMessage] = useState("给我推荐 5 首适合深夜写代码听的歌。");
  const [messages, setMessages] = useState<ChatMessage[]>([]);

  async function startChat(event: FormEvent) {
    event.preventDefault();
    await submitMessage();
  }

  async function submitMessage() {
    if (!message.trim()) {
      return;
    }

    const userText = message.trim();
    const userMessage: ChatMessage = {
      id: crypto.randomUUID(),
      role: "user",
      content: userText,
      state: "done"
    };

    setMessages((current) => [...current, userMessage]);
    setMessage("");
    onBusyChange(true);
    try {
      const run = await chatClient.startChat(userText);
      const agentMessageId = crypto.randomUUID();
      setMessages((current) => [
        ...current,
        {
          id: agentMessageId,
          role: "agent",
          content: "",
          state: "streaming",
          runId: run.runId
        }
      ]);
      onEvent({ id: crypto.randomUUID(), name: "run", detail: `已创建任务：${run.runId}` });
      chatClient.openRunEvents(run.runId, {
        onMessageDelta: (detail, eventRunId) => {
          setMessages((current) =>
            current.map((item) =>
              item.role === "agent" && item.runId === (eventRunId ?? run.runId)
                ? { ...item, content: item.content + detail, state: "streaming" }
                : item
            )
          );
        },
        onToolStart: (detail) => onEvent({ id: crypto.randomUUID(), name: "tool_start", detail }),
        onToolResult: (detail) => onEvent({ id: crypto.randomUUID(), name: "tool_result", detail }),
        onSongCards: (songs) => {
          onSongs(songs);
          onEvent({ id: crypto.randomUUID(), name: "song_cards", detail: `收到 ${songs.length} 首歌曲` });
        },
        onError: (detail) => {
          setMessages((current) =>
            current.map((item) =>
              item.role === "agent" && item.runId === run.runId
                ? { ...item, content: item.content || detail, state: "error" }
                : item
            )
          );
          onEvent({ id: crypto.randomUUID(), name: "agent_error", detail });
          onBusyChange(false);
        },
        onDone: () => {
          setMessages((current) =>
            current.map((item) =>
              item.role === "agent" && item.runId === run.runId ? { ...item, state: "done" } : item
            )
          );
          onBusyChange(false);
        }
      });
    } catch (error) {
      const detail = error instanceof Error ? error.message : "未知错误";
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: "agent",
          content: detail,
          state: "error"
        }
      ]);
      onBusyChange(false);
      onEvent({
        id: crypto.randomUUID(),
        name: "error",
        detail
      });
    }
  }

  function handleTextareaKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing) {
      return;
    }

    event.preventDefault();
    if (!busy && message.trim()) {
      void submitMessage();
    }
  }

  return (
    <section className="panel command-panel">
      <div className="panel-heading">
        <h2 className="chat-panel-title">MUSIO</h2>
        <span>{busy ? "运行中" : "空闲"}</span>
      </div>
      {disabledReason ? <p className="access-note">{disabledReason}</p> : null}
      <AgentMessageList messages={messages} />
      <form onSubmit={startChat} className="prompt-form">
        <textarea
          placeholder="Say something to Musio..."
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          onKeyDown={handleTextareaKeyDown}
        />
        <button type="submit" disabled={busy || !message.trim()}>
          <ArrowUp size={18} />
        </button>
      </form>
    </section>
  );
}
