## Introduction

Aquafish is an extensible content, community and AI platform. It combines a
blog, CMS, forum, user center, theme system and administration console in one
deployable application.

## Deployment

- The service listens on container port `8520`.
- Persistent files are stored in the installation directory under `data/`.
- MySQL, MariaDB and PostgreSQL services managed by 1Panel are supported.
- Create a website in 1Panel and reverse proxy it to Aquafish after installing.
- Open `/setup` to initialize the site and administrator account.

## Links

- Source: https://github.com/yubboo/aquafish-dev
- Container image: https://github.com/users/yubboo/packages/container/package/aquafish
