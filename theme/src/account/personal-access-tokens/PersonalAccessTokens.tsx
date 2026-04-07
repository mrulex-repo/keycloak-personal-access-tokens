import { useState, useEffect } from "react";
import { Spinner, Title } from "@patternfly/react-core";
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

  useEffect(() => {
    const ctrl = new AbortController();
    Promise.all([listPats(ctrl.signal, ctx), listRoles(ctrl.signal, ctx)]).then(
      ([p, r]) => {
        setPats(p);
        setRoles(r);
        setLoading(false);
      },
    );
    return () => ctrl.abort();
  }, []); // ctx values are stable for the session lifetime

  return { ctx, pats, roles, loading, setPats };
}

function toListItem(pat: PatCreated): PatListItem {
  return { id: pat.id, name: pat.name, created: pat.created, expires: pat.expires, roles: pat.roles };
}

export default function PersonalAccessTokens() {
  const { ctx, pats, roles, loading, setPats } = usePatData();
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

  return (
    <>
      <Title headingLevel="h1">{msg.pageTitle}</Title>
      <p>{msg.pageDescription}</p>
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
    </>
  );
}
