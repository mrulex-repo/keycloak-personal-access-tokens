import { useState, useEffect } from "react";
import { PageSection, Spinner, Text, TextContent, Title } from "@patternfly/react-core";
import { listPats, listRoles, deletePat } from "./pat-client";
import type { PatListItem, PatCreated, PatRole } from "./types";
import { usePatApiContext } from "./usePatApiContext";
import { PatList } from "./components/PatList";
import { PatCreateForm } from "./components/PatCreateForm";
import { NewTokenAlert } from "./components/NewTokenAlert";
import { DeleteConfirmModal } from "./components/DeleteConfirmModal";
import { msg } from "./i18n";

function usePatData() {
  const ctx = usePatApiContext();
  const [pats, setPats] = useState<PatListItem[]>([]);
  const [roles, setRoles] = useState<PatRole[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const ctrl = new AbortController();
    Promise.all([listPats(ctrl.signal, ctx), listRoles(ctrl.signal, ctx)]).then(
      ([p, r]) => {
        setPats(p);
        setRoles(r);
        setLoading(false);
      },
    ).catch((e: unknown) => {
      if (e instanceof Error && e.name === "AbortError") return;
      setError(e instanceof Error ? e.message : "Failed to load personal access tokens");
      setLoading(false);
    });
    return () => ctrl.abort();
  }, [ctx]); // ctx is stable for the session lifetime — single render cycle, no re-trigger risk

  return { ctx, pats, roles, loading, error, setPats };
}

function toListItem(pat: PatCreated): PatListItem {
  return { id: pat.id, name: pat.name, created: pat.created, expires: pat.expires, roles: pat.roles };
}

export default function PersonalAccessTokens() {
  const { ctx, pats, roles, loading, error, setPats } = usePatData();
  const [createdPat, setCreatedPat] = useState<PatCreated | null>(null);
  const [deletingPat, setDeletingPat] = useState<PatListItem | null>(null);

  function handleCreated(pat: PatCreated) {
    setPats((prev) => [...prev, toListItem(pat)]);
    setCreatedPat(pat);
  }

  function handleDeleted(id: string) {
    setPats((prev) => prev.filter((p) => p.id !== id));
    setDeletingPat(null);
  }

  if (loading) return <Spinner />;
  if (error) return <p style={{ color: "red" }}>{error}</p>;

  return (
    <>
      <PageSection variant="light">
        <TextContent>
          <Title headingLevel="h1" data-testid="page-heading">{msg.pageTitle}</Title>
          <Text component="p">{msg.pageDescription}</Text>
        </TextContent>
      </PageSection>
      <PageSection variant="light">
        {createdPat && (
          <NewTokenAlert pat={createdPat} onDone={() => setCreatedPat(null)} />
        )}
        <PatCreateForm ctx={ctx} roles={roles} onCreated={handleCreated} />
        <PatList pats={pats} onDelete={setDeletingPat} />
        {deletingPat && (
          <DeleteConfirmModal
            name={deletingPat.name}
            onConfirm={() =>
              deletePat(ctx, deletingPat.id).then(() =>
                handleDeleted(deletingPat.id),
              )
            }
            onCancel={() => setDeletingPat(null)}
          />
        )}
      </PageSection>
    </>
  );
}
