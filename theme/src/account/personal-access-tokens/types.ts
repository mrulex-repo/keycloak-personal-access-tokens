export type PatListItem = {
  id: string;
  name: string;
  created: string;
  expires: string | null;
  roles: string[];
};

export type PatCreated = {
  id: string;
  name: string;
  token: string;
  created: string;
  expires: string | null;
  roles: string[];
};

export type PatRole = {
  name: string;
  description: string;
};

export type PatCreateRequest = {
  name: string;
  roles: string[];
  expires?: string;
};
