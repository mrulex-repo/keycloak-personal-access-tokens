import { useState } from "react";
import {
  Button,
  InputGroup,
  InputGroupItem,
  TextInput,
} from "@patternfly/react-core";
import { CopyIcon, EyeIcon, EyeSlashIcon } from "@patternfly/react-icons";

type Props = { token: string };

export function Token({ token }: Props) {
  const [revealed, setRevealed] = useState(false);
  const [copied, setCopied] = useState(false);

  function copyToClipboard() {
    navigator.clipboard.writeText(token).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }

  return (
    <InputGroup>
      <InputGroupItem isFill>
        <TextInput
          value={revealed ? token : "•".repeat(token.length)}
          readOnlyVariant="plain"
          aria-label="Personal access token"
        />
      </InputGroupItem>
      <InputGroupItem>
        <Button
          variant="control"
          onClick={() => setRevealed(!revealed)}
          aria-label={revealed ? "Hide token" : "Show token"}
        >
          {revealed ? <EyeSlashIcon /> : <EyeIcon />}
        </Button>
      </InputGroupItem>
      <InputGroupItem>
        <Button
          variant="control"
          onClick={copyToClipboard}
          aria-label={copied ? "Copied" : "Copy token"}
        >
          <CopyIcon />
        </Button>
      </InputGroupItem>
    </InputGroup>
  );
}
