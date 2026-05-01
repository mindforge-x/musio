import { useEffect, useRef } from "react";
import { ChatMessage } from "./chatTypes";
import { MarkdownContent } from "./MarkdownContent";

type AgentMessageListProps = {
  messages: ChatMessage[];
};

export function AgentMessageList({ messages }: AgentMessageListProps) {
  const listRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [messages]);

  if (messages.length === 0) {
    return (
      <div className="chat-message-list empty">
        <p>和 Musio 说说你此刻想听什么。</p>
      </div>
    );
  }

  return (
    <div ref={listRef} className="chat-message-list" aria-live="polite">
      {messages.map((message) => (
        <article key={message.id} className={`chat-message ${message.role} ${message.state}`}>
          {message.role === "agent" ? <div className="chat-avatar">M</div> : null}
          <div className="chat-bubble">
            <span>{message.role === "agent" ? "MUSIO" : "YOU"}</span>
            {message.role === "agent" ? (
              <MarkdownContent text={message.content || (message.state === "streaming" ? "正在听你说完，也在认真想..." : "")} />
            ) : (
              <p>{message.content}</p>
            )}
            {message.state === "streaming" ? <small>正在回复</small> : null}
            {message.state === "error" ? <small>回复中断</small> : null}
          </div>
          {message.role === "user" ? <div className="chat-avatar user">Y</div> : null}
        </article>
      ))}
    </div>
  );
}
