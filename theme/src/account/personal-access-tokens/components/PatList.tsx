import {
  Button,
  DataList,
  DataListAction,
  DataListCell,
  DataListItem,
  DataListItemCells,
  DataListItemRow,
  Label,
  LabelGroup,
} from "@patternfly/react-core";
import { useState } from "react";
import type { PatListItem } from "../types";
import { msg } from "../i18n";

type Props = { pats: PatListItem[]; onDelete: (pat: PatListItem) => void };

export function PatList({ pats, onDelete }: Props) {
  if (pats.length === 0) return <p>{msg.noTokens}</p>;

  return (
    <DataList aria-label={msg.pageTitle}>
      {pats.map((pat) => (
        <PatRow key={pat.id} pat={pat} onDelete={onDelete} />
      ))}
    </DataList>
  );
}

function PatRow({ pat, onDelete }: { pat: PatListItem; onDelete: (pat: PatListItem) => void }) {
  return (
    <DataListItem id={pat.id}>
      <DataListItemRow>
        <DataListItemCells
          dataListCells={[
            <DataListCell key="name">
              <strong>{pat.name}</strong>
            </DataListCell>,
            <DataListCell key="roles">
              <RoleBadges roles={pat.roles} />
            </DataListCell>,
            <DataListCell key="created">{formatDate(pat.created)}</DataListCell>,
            <DataListCell key="expires">
              <ExpiryCell expires={pat.expires} />
            </DataListCell>,
          ]}
        />
        <DataListAction
          id={`${pat.id}-action`}
          aria-label={msg.actions}
          aria-labelledby={`${pat.id} ${pat.id}-action`}
        >
          <Button variant="danger" onClick={() => onDelete(pat)}>
            {msg.delete}
          </Button>
        </DataListAction>
      </DataListItemRow>
    </DataListItem>
  );
}

function RoleBadges({ roles }: { roles: string[] }) {
  return (
    <LabelGroup>
      {roles.map((role) => (
        <Label key={role} color="blue">
          {role}
        </Label>
      ))}
    </LabelGroup>
  );
}

function ExpiryCell({ expires }: { expires: string | null }) {
  const [now] = useState(Date.now);

  if (!expires) return <span>{msg.noExpiry}</span>;
  const expiryTime = new Date(expires).getTime();
  const isExpired = expiryTime < now;
  const isExpiringSoon = !isExpired && expiryTime - now < 7 * 24 * 60 * 60 * 1000;

  return (
    <>
      {formatDate(expires)}{" "}
      {isExpired && (
        <Label color="red" isCompact>
          {msg.expired}
        </Label>
      )}
      {isExpiringSoon && (
        <Label color="orange" isCompact>
          {msg.expiringSoon}
        </Label>
      )}
    </>
  );
}

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(
    new Date(iso),
  );
}
