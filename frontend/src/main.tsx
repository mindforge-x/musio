import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import "./styles.css";

installRandomUUIDFallback();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

function installRandomUUIDFallback() {
  const existingCrypto = typeof globalThis.crypto === "undefined" ? undefined : globalThis.crypto;
  if (typeof existingCrypto?.randomUUID === "function") {
    return;
  }

  const fallbackRandomUUID = () => {
    const bytes = new Uint8Array(16);
    if (typeof existingCrypto?.getRandomValues === "function") {
      existingCrypto.getRandomValues(bytes);
    } else {
      for (let index = 0; index < bytes.length; index += 1) {
        bytes[index] = Math.floor(Math.random() * 256);
      }
    }

    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;

    const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0"));
    return [
      hex.slice(0, 4).join(""),
      hex.slice(4, 6).join(""),
      hex.slice(6, 8).join(""),
      hex.slice(8, 10).join(""),
      hex.slice(10).join("")
    ].join("-");
  };

  if (existingCrypto) {
    try {
      Object.defineProperty(existingCrypto, "randomUUID", {
        configurable: true,
        value: fallbackRandomUUID
      });
      return;
    } catch {
      // Some browsers expose a non-extensible crypto object on insecure origins.
    }
  }

  Object.defineProperty(globalThis, "crypto", {
    configurable: true,
    value: {
      randomUUID: fallbackRandomUUID
    }
  });
}
