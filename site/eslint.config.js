import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";
import eslintPluginSvelte from "eslint-plugin-svelte";
import eslintPluginAstro from "eslint-plugin-astro";

export default [
    {
        ignores: [
            "dist/",
            "node_modules/",
            ".astro/",
            "src/components/posthog.astro",
        ],
    },
    js.configs.recommended,
    ...tseslint.configs.recommended,
    ...eslintPluginSvelte.configs.recommended,
    ...eslintPluginAstro.configs.recommended,
    {
        languageOptions: {
            globals: {
                ...globals.browser,
                ...globals.node,
            },
        },
    },
    {
        files: ["**/*.svelte", "**/*.svelte.ts"],
        languageOptions: {
            parserOptions: {
                parser: tseslint.parser,
            },
        },
    },
    {
        rules: {
            "no-console": "warn",
            "@typescript-eslint/no-explicit-any": "off",
            "@typescript-eslint/no-unused-vars": [
                "warn",
                { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
            ],
        },
    },
    {
        files: ["**/*.svelte"],
        rules: {
            "svelte/require-each-key": "error",
            "svelte/infinite-reactive-loop": "error",
            "svelte/no-at-html-tags": "warn",
            "svelte/prefer-svelte-reactivity": "warn",
        },
    },
];
