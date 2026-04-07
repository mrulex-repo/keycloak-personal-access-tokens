import { useState } from "react";
import {
  Button,
  Modal,
  ModalBoxHeader,
  ModalBoxBody,
  ModalBoxFooter,
} from "@patternfly/react-core";
import { msg } from "../i18n";

type Props = {
  name: string;
  onConfirm: () => Promise<void>;
  onCancel: () => void;
};

export function DeleteConfirmModal({ name, onConfirm, onCancel }: Props) {
  const [deleting, setDeleting] = useState(false);

  function handleConfirm() {
    setDeleting(true);
    onConfirm().finally(() => setDeleting(false));
  }

  return (
    <Modal isOpen onClose={onCancel} variant="small" aria-label={msg.deleteTitle}>
      <ModalBoxHeader>{msg.deleteTitle}</ModalBoxHeader>
      <ModalBoxBody>{msg.deleteConfirm(name)}</ModalBoxBody>
      <ModalBoxFooter>
        <Button variant="danger" isLoading={deleting} onClick={handleConfirm}>
          {msg.delete}
        </Button>
        <Button variant="link" onClick={onCancel}>
          {msg.cancel}
        </Button>
      </ModalBoxFooter>
    </Modal>
  );
}
