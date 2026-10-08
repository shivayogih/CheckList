"""CheckList CI agents (Phase 11, CL-220 to CL-229).

Deterministic, rule-based agents built on Google ADK workflows. They run in GitHub Actions only,
need no API key and never call a model unless the optional enrichment step is switched on by a
``GEMINI_API_KEY`` secret (see ``enrich.py`` and docs/ai-automation.md).

The rules live in plain Python modules (``translations``, ``release_notes``, ``issue_sync``) so
they are easy to test; ``workflows`` wraps each one in an ADK ``Workflow`` graph.
"""
