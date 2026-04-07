import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { KcAccountUI } from "./KcAccountUI";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <KcAccountUI />
  </StrictMode>,
);
