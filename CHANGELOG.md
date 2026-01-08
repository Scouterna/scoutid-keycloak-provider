# Changelog

## [0.8.0](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.7.0...v0.8.0) (2026-01-08)


### Features

* add group email ([9d2f727](https://github.com/Scouterna/scoutid-keycloak-provider/commit/9d2f7270c0e1892768c90bce573e9a8391002738))
* add group logic ([f35cf54](https://github.com/Scouterna/scoutid-keycloak-provider/commit/f35cf54bbe2e97740206a4a053469b2b3a2a0ae7))


### Bug Fixes

* check that duplicate emails are not allowed ([070df52](https://github.com/Scouterna/scoutid-keycloak-provider/commit/070df5209f9623f72d4e5cb2322fcb47d2c53317))
* group email duplication logic error ([473aea4](https://github.com/Scouterna/scoutid-keycloak-provider/commit/473aea46117b18adbae8294d1d56b32b0fa93d4c))
* improve domain name handling ([c044772](https://github.com/Scouterna/scoutid-keycloak-provider/commit/c044772ed3ca6db33c79a40da90b3a3426a7f392))
* remove unneccesary integration tests ([b01f42b](https://github.com/Scouterna/scoutid-keycloak-provider/commit/b01f42b023042941f017491f8e8a7869b1725ad7))
* scoutkår id corrected ([23cf81e](https://github.com/Scouterna/scoutid-keycloak-provider/commit/23cf81e6c7987f64b77f805cda7c867baca28dc4))
* simplified text normalisation ([cad46bf](https://github.com/Scouterna/scoutid-keycloak-provider/commit/cad46bf2f5d0de88dea1da2e6fdccee9b68a5463))


### Documentation

* client config update for Google workspace ([f04dccb](https://github.com/Scouterna/scoutid-keycloak-provider/commit/f04dccb6cde5ca3b48ad7746b9f1be10f56b1eda))
* complete Google Workspace instructions ([fd447f9](https://github.com/Scouterna/scoutid-keycloak-provider/commit/fd447f9155f7527d709cfd0951fe55a3daed07eb))
* Include client configuration data ([4ae908d](https://github.com/Scouterna/scoutid-keycloak-provider/commit/4ae908d12adc12619f8937fb93d8ba9a2f191f81))

## [0.7.0](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.6.0...v0.7.0) (2026-01-02)


### Features

* add email-like first.last format ([0fefc7b](https://github.com/Scouterna/scoutid-keycloak-provider/commit/0fefc7bfe2027691cf02b39c0795faae1809247a))


### Documentation

* added first.last name format to example config ([75d51a4](https://github.com/Scouterna/scoutid-keycloak-provider/commit/75d51a430e706f74f64ce2c832209d8580613ca6))
* clarified testing procedures ([083c68b](https://github.com/Scouterna/scoutid-keycloak-provider/commit/083c68b052b064c186a390e0c60b2a914fe4cf2b))

## [0.6.0](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.5.1...v0.6.0) (2025-12-29)


### Features

* fake local email added as attribute ([2fde8a2](https://github.com/Scouterna/scoutid-keycloak-provider/commit/2fde8a252b6dab4606e1cf8921fbdc9663bbde50))


### Bug Fixes

* force reload profile on provider update ([b87e86a](https://github.com/Scouterna/scoutid-keycloak-provider/commit/b87e86a06d7e9ebc27f38b091a4ae70849e7f882))

## [0.5.1](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.5.0...v0.5.1) (2025-12-29)


### Performance Improvements

* optimize profile updates with SHA-256 hash-based change detection ([1fc6e16](https://github.com/Scouterna/scoutid-keycloak-provider/commit/1fc6e1654b8ec9f652ca4a944fa38e1623dede9e))

## [0.5.0](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.4.1...v0.5.0) (2025-12-29)


### ⚠ BREAKING CHANGES

* username convention updated

### Features

* add scouterna epost field ([e7a12c4](https://github.com/Scouterna/scoutid-keycloak-provider/commit/e7a12c4db66b43885986a970f4b2e17a0686d0c7))
* improved logging and error tracing ([2af9773](https://github.com/Scouterna/scoutid-keycloak-provider/commit/2af97739e1a0290ddb57e17fdc34a4942065c565))
* parse non-compliant personnummer format ([0e6e3f2](https://github.com/Scouterna/scoutid-keycloak-provider/commit/0e6e3f2c7eb95360929704284e6e3de89326415c))
* set scoutnet url as env variable ([4531bf8](https://github.com/Scouterna/scoutid-keycloak-provider/commit/4531bf88e896a82617c8ff8f6eb367c7026eb817))


### Bug Fixes

* integration tests incl docs ([63c7314](https://github.com/Scouterna/scoutid-keycloak-provider/commit/63c731407fb3da5848b1241f1cbe7c5a3526d0fa))
* username convention updated ([a5be45c](https://github.com/Scouterna/scoutid-keycloak-provider/commit/a5be45cc3f2958453c0e239e837b5ee96117c20e))


### Performance Improvements

* optimize HTTP client for high-concurrency scenarios ([d2837fd](https://github.com/Scouterna/scoutid-keycloak-provider/commit/d2837fdd5ac12fd86ddb9a91782e3add6cb91623))


### Documentation

* gitignore for tmp folder ([3449228](https://github.com/Scouterna/scoutid-keycloak-provider/commit/3449228542d447ea9069abadfe06abdc17c272dc))
* update config example ([65382b3](https://github.com/Scouterna/scoutid-keycloak-provider/commit/65382b32baf5734773bf3883a681adcfa6f7e98f))

## [0.4.1](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.4.0...v0.4.1) (2025-12-14)


### Bug Fixes

* add theme test workflow ([8f140ed](https://github.com/Scouterna/scoutid-keycloak-provider/commit/8f140ed2a39e8ac371a45645fbadbc048e4c6ab5))


### Documentation

* add theme to family list ([a6f3bf6](https://github.com/Scouterna/scoutid-keycloak-provider/commit/a6f3bf6e6c63811a10f696eef1b76814586ee71f))

## [0.4.0](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.3.2...v0.4.0) (2025-11-30)


### Features

* fetch profile image ([4e88a2b](https://github.com/Scouterna/scoutid-keycloak-provider/commit/4e88a2b9c0f791e2c3a0e4ee1291cd6268107e56))
* fetching user roles ([8b51a8c](https://github.com/Scouterna/scoutid-keycloak-provider/commit/8b51a8c119563b7be694fa2032bf20f9f972e4a4))


### Bug Fixes

* disable profileImage fetch until API is fixed ([6eade2c](https://github.com/Scouterna/scoutid-keycloak-provider/commit/6eade2c362bb1eba1670c22038d26c468a09e96c))

## [0.3.2](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.3.1...v0.3.2) (2025-11-23)


### Bug Fixes

* reduce user data usage ([c8741f2](https://github.com/Scouterna/scoutid-keycloak-provider/commit/c8741f285c472c1778e01a44bf1e7b78d9ac08ea))
* version bump to 26.4.5 ([d47bbdb](https://github.com/Scouterna/scoutid-keycloak-provider/commit/d47bbdb1b4f343ec6ec2a9ebc8a9f8a1d051db7e))


### Documentation

* add process for member_no as sub ([37acf3b](https://github.com/Scouterna/scoutid-keycloak-provider/commit/37acf3b2051473b02e90a88593f9c6808d63df58))

## [0.3.1](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.3.0...v0.3.1) (2025-10-28)


### Bug Fixes

* broker_user_id added, address data removed ([dbe77c1](https://github.com/Scouterna/scoutid-keycloak-provider/commit/dbe77c18200cc006689d92244febdd1fbd174857))

## [0.3.0](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.2.0...v0.3.0) (2025-10-23)


### Features

* added profile fetch and sync into keycloak ([dfcec2a](https://github.com/Scouterna/scoutid-keycloak-provider/commit/dfcec2a485660d8400d2ed7ca6e957408402f8ad))

## [0.2.0](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.1.2...v0.2.0) (2025-10-11)


### Features

* dummy commit ([b0cc3a6](https://github.com/Scouterna/scoutid-keycloak-provider/commit/b0cc3a6a45aba9dede0c46eed8be59ddf93b9ba2))
* login form works ([2e4b597](https://github.com/Scouterna/scoutid-keycloak-provider/commit/2e4b59750c8ca66d370ee34a281c96c5d383501e))


### Bug Fixes

* dummy commit ([901f3bf](https://github.com/Scouterna/scoutid-keycloak-provider/commit/901f3bf5aceeee3bbe17c23d1f0eaee5a51d1d7a))
* version error and setup in README ([3d588f2](https://github.com/Scouterna/scoutid-keycloak-provider/commit/3d588f2ec162fe826583c0d43479f7c633cb8ac2))


### Documentation

* add section on release-please and commit format ([17854bf](https://github.com/Scouterna/scoutid-keycloak-provider/commit/17854bfddd53ee1c578cce3605ff32a51ccca675))

## [0.1.2](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.1.1...v0.1.2) (2025-10-10)


### Bug Fixes

* dummy commit ([5e140cd](https://github.com/Scouterna/scoutid-keycloak-provider/commit/5e140cd90600f50cd7cd48d7348d1b448046b9cf))

## [0.1.1](https://github.com/Scouterna/scoutid-keycloak-provider/compare/v0.1.0...v0.1.1) (2025-10-10)


### Bug Fixes

* dummy commit ([0418078](https://github.com/Scouterna/scoutid-keycloak-provider/commit/0418078635fbb2b68d3108b3c217e9073be7ca21))

## 0.1.0 (2025-10-10)


### Features

* add metadata and set up GitHub Actions ([038193c](https://github.com/Scouterna/scoutid-keycloak-provider/commit/038193c6e368b089ffbee025360d4b61ca068863))


### Documentation

* add logo ([cb513ae](https://github.com/Scouterna/scoutid-keycloak-provider/commit/cb513ae633d3d2eb92216ce251d1baa3d2d6ead4))
* add note on certs only needing to be generated once ([ee5e3dd](https://github.com/Scouterna/scoutid-keycloak-provider/commit/ee5e3dd322c0778e80baec153e6aab82f617d953))
* add readme ([e2b3b8e](https://github.com/Scouterna/scoutid-keycloak-provider/commit/e2b3b8ec9f2c0b24480d74fc107b27b2bf605a41))


### Miscellaneous Chores

* set initial version ([d178b5a](https://github.com/Scouterna/scoutid-keycloak-provider/commit/d178b5a3622c4d87c8e80327f94ffa21eac98c76))
