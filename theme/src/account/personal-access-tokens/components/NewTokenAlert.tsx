import { Alert, AlertActionLink } from "@patternfly/react-core";
import { Token } from "./Token";
import type { PatCreated } from "../types";
import { msg } from "../i18n";

type Props = { pat: PatCreated; onDone: () => void };

export function NewTokenAlert({ pat, onDone }: Props) {
  return (
    <Alert
      variant="warning"
      title={msg.copyTokenNow}
      actionLinks={<AlertActionLink onClick={onDone}>{msg.done}</AlertActionLink>}
    >
      <Token token={pat.token} />
    </Alert>
  );
}
