# Language tool bundles

The repository UI downloads one bundle for the selected source language. A bundle is deliberately language-scoped and contains:

- the matching `parser-*` CLI;
- the matching `extract-*` CLI;
- the endpoint-rule-author Skill shipped by static-extract;
- `start.sh`, which lets the user choose a project and Agent and writes the generated `.ser` ZIP to the desktop;
- `install.sh`, which installs both commands and the bundled Skill without requiring a separate runtime package.

The download names are stable:

```text
https://github.com/praha-poseidon/code-graph-engine/releases/latest/download/codegraph-tools-<language>-linux-x64.tar.gz
```

JavaScript and TypeScript intentionally share the `javascript` bundle because they use the same parser and extractor.

## Publishing

Run the `Publish language tool bundles` workflow manually with a version such as `v0.1.0`, or push a `tools-v0.1.0` tag. The workflow checks out each parser and static-extract source repository, builds the self-contained packages with their language toolchain, assembles the pair, and publishes one engine release containing the seven bundles and checksums. It does not require parser releases to have been published first.

The local assembly command is useful for validating package layout without publishing:

```bash
./packaging/build-language-bundle.sh \
  java v0.1.0 \
  /path/to/parser-java-vX-linux-x64.tar.gz \
  /path/to/extract-java-vY-linux-x64.tar.gz \
  ./dist
```

The package does not combine unrelated languages and does not require the engine repository at runtime.
