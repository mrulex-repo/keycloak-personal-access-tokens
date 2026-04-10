import js from "@eslint/js";
import tseslint from "typescript-eslint";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import eslintComments from "@eslint-community/eslint-plugin-eslint-comments";

export default tseslint.config(
  // Auto-generated files are excluded entirely
  { ignores: ["dist", "dist_keycloak", "public", "src/kc.gen.tsx"] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ["**/*.{ts,tsx}"],
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
      "@eslint-community/eslint-comments": eslintComments,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      "react-refresh/only-export-components": [
        "warn",
        { allowConstantExport: true },
      ],

      // ===== NAMING (mirrors Checkstyle §5) =====

      "@typescript-eslint/naming-convention": [
        "error",
        // Types, interfaces, classes, enums: PascalCase
        {
          selector: "typeLike",
          format: ["PascalCase"],
        },
        // Standalone _ is allowed (ignored callback params)
        {
          selector: "parameter",
          filter: { regex: "^_+$", match: true },
          format: null,
        },
        // Parameters: camelCase; leading _ allowed for intentionally unused
        {
          selector: "parameter",
          format: ["camelCase"],
          leadingUnderscore: "allow",
        },
        // Variables and functions: camelCase for logic, PascalCase for React
        // components, UPPER_CASE for module-level constants (ConstantName rule)
        {
          selector: ["variable", "function"],
          format: ["camelCase", "PascalCase", "UPPER_CASE"],
        },
      ],

      // ===== IMPORTS (mirrors AvoidStarImport) =====

      "no-restricted-syntax": [
        "error",
        {
          selector: "ImportNamespaceSpecifier",
          message:
            "Wildcard imports are not allowed. Import only what you need.",
        },
      ],

      // ===== STRUCTURAL LIMITS (mirrors CLAUDE.md rules) =====

      // Rule #3: functions ≤ 35 non-blank lines
      "max-lines-per-function": [
        "error",
        { max: 35, skipBlankLines: true, skipComments: true },
      ],

      // Rule #4: max 3 parameters per function
      "max-params": ["error", { max: 3 }],

      // Rule #5: max 2 nesting levels
      "max-depth": ["error", 2],

      // ===== UNUSED CODE =====

      // Unused variables/imports are already caught by tsconfig noUnusedLocals +
      // noUnusedParameters; this mirrors that enforcement at the ESLint layer.
      "@typescript-eslint/no-unused-vars": "error",

      // Expressions whose result is discarded (e.g. `a && b` with no assignment)
      "@typescript-eslint/no-unused-expressions": "error",

      // ===== NO RULE SUPPRESSION =====

      // Prevents eslint-disable / eslint-disable-next-line / eslint-env comments.
      // Rules must be fixed, not silenced.
      "@eslint-community/eslint-comments/no-use": "error",
    },
  },
  // Build config files are not application code — exempt from function-length limit
  // (mirrors Checkstyle's fileExtensions="java" which excludes build scripts)
  {
    files: ["*.config.ts"],
    rules: {
      "max-lines-per-function": "off",
    },
  }
);
