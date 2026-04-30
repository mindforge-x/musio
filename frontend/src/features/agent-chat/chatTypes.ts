export type ChatMessageRole = "user" | "agent";

export type ChatMessageState = "pending" | "streaming" | "done" | "error";

export type ChatMessage = {
  id: string;
  role: ChatMessageRole;
  content: string;
  state: ChatMessageState;
  runId?: string;
};
