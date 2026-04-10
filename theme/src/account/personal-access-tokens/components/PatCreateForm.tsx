import { useState } from "react";
import {
  ActionGroup,
  Button,
  Form,
  FormGroup,
  HelperText,
  HelperTextItem,
  MenuToggle,
  Select,
  SelectList,
  SelectOption,
  TextInput,
} from "@patternfly/react-core";
import type { MenuToggleElement } from "@patternfly/react-core";
import { createPat } from "../pat-client";
import type { PatApiContext } from "../pat-client";
import type { PatCreated, PatRole } from "../types";
import { msg } from "../i18n";

const NAME_PATTERN = /^[a-z][a-z0-9_-]{0,63}$/;

function validateName(name: string): string | undefined {
  if (!name) return msg.nameRequired;
  if (!NAME_PATTERN.test(name)) return msg.nameInvalid;
  return undefined;
}

function dateInputToIso(dateValue: string): string {
  return new Date(dateValue + "T23:59:59Z").toISOString();
}

type FormErrors = { name?: string; roles?: string; server?: string };

function useCreatePatForm(ctx: PatApiContext, onCreated: (pat: PatCreated) => void) {
  const [name, setName] = useState("");
  const [selectedRoles, setSelectedRoles] = useState<string[]>([]);
  const [expiresDate, setExpiresDate] = useState<string | undefined>();
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);

  function toggleRole(role: string) {
    setSelectedRoles((prev) =>
      prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role],
    );
  }

  function reset() {
    setName("");
    setSelectedRoles([]);
    setExpiresDate(undefined);
    setErrors({});
  }

  function submit() {
    const nameErr = validateName(name);
    const rolesErr = selectedRoles.length === 0 ? msg.rolesRequired : undefined;
    setErrors({ name: nameErr, roles: rolesErr });
    if (nameErr || rolesErr) return;
    setSubmitting(true);
    const expires = expiresDate ? dateInputToIso(expiresDate) : undefined;
    createPat(ctx, { name, roles: selectedRoles, expires })
      .then((pat) => { onCreated(pat); reset(); })
      .catch((err: Error) => setErrors((e) => ({ ...e, server: err.message })))
      .finally(() => setSubmitting(false));
  }

  return { name, setName, selectedRoles, toggleRole, expiresDate, setExpiresDate, errors, submitting, submit };
}

function RoleSelect({ roles, selected, onToggle }: {
  roles: PatRole[];
  selected: string[];
  onToggle: (role: string) => void;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const toggleText = selected.length > 0 ? selected.join(", ") : msg.selectRoles;

  return (
    <Select
      isOpen={isOpen}
      onOpenChange={setIsOpen}
      toggle={(ref: React.Ref<MenuToggleElement>) => (
        <MenuToggle ref={ref} onClick={() => setIsOpen((o) => !o)} isExpanded={isOpen}>
          {toggleText}
        </MenuToggle>
      )}
      onSelect={(_, value) => onToggle(value as string)}
      aria-label={msg.rolesLabel}
    >
      <SelectList>
        {roles.map((role) => (
          <SelectOption key={role.name} value={role.name} hasCheckbox isSelected={selected.includes(role.name)}>
            {role.name}
          </SelectOption>
        ))}
      </SelectList>
    </Select>
  );
}

function FieldError({ message, id }: { message?: string; id?: string }) {
  if (!message) return null;
  return (
    <HelperText>
      <HelperTextItem variant="error" id={id}>{message}</HelperTextItem>
    </HelperText>
  );
}

type Props = {
  ctx: PatApiContext;
  roles: PatRole[];
  onCreated: (pat: PatCreated) => void;
};

export function PatCreateForm({ ctx, roles, onCreated }: Props) {
  const form = useCreatePatForm(ctx, onCreated);

  return (
    <Form>
      <FormGroup label={msg.nameLabel} isRequired fieldId="pat-name">
        <TextInput id="pat-name" value={form.name} onChange={(_, v) => form.setName(v)} />
        <FieldError message={form.errors.name} />
      </FormGroup>
      <FormGroup label={msg.rolesLabel} isRequired fieldId="pat-roles">
        <RoleSelect roles={roles} selected={form.selectedRoles} onToggle={form.toggleRole} />
        <FieldError message={form.errors.roles} />
      </FormGroup>
      <FormGroup label={msg.expiresLabel} fieldId="pat-expires">
        <TextInput
          type="date"
          id="pat-expires"
          value={form.expiresDate ?? ""}
          onChange={(_, v) => form.setExpiresDate(v || undefined)}
        />
      </FormGroup>
      <FieldError message={form.errors.server} id="pat-server-error" />
      <ActionGroup>
        <Button variant="primary" isLoading={form.submitting} onClick={form.submit}>
          {msg.createToken}
        </Button>
      </ActionGroup>
    </Form>
  );
}
