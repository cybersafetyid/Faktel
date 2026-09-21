# Security Policy

## Supported versions

While the project is pre-1.0, security fixes are provided for the **latest released minor version** only.

## Reporting a vulnerability

**Do not open a public issue.** Use GitHub's private reporting:
[Report a vulnerability](https://github.com/cybersafetyid/Faktel/security/advisories/new).

Please include: affected version/module, platform, a description and impact, and reproduction steps.
**Do not attach real personal images or identity documents.**

We aim to acknowledge reports within 5 working days and to agree a fix and disclosure timeline with you.

## Scope

In scope: vulnerabilities in Faktel's code (memory-safety/logic issues in image handling, decoding or pipelines, unsafe
handling of untrusted images, supply-chain issues in the build/release process).

Out of scope: the accuracy limits documented in the README (e.g. passive liveness can be defeated by a determined
attacker; KTP validation does not detect forgeries), and vulnerabilities in third-party runtimes or models (report those
upstream; we will update the dependency).

## Handling of untrusted images

Faktel decodes images with the platform's native codecs and processes them in bounded, bounds-checked Kotlin code.
Applications should still apply their own size limits to user-supplied files before decoding.
