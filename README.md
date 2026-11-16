Nostrino is a Nostr SDK for Kotlin.

[<img src="https://img.shields.io/nexus/r/app.cash.nostrino/nostr-sdk.svg?label=latest%20release&server=https%3A%2F%2Foss.sonatype.org"/>](https://central.sonatype.com/namespace/app.cash.nostrino)

It is a work in progress. See current [NIP support](#supported-nips).


## Getting Started

On the [Sontaype page for Nostrino](https://central.sonatype.com/namespace/app.cash.nostrino), choose the latest version
of `nostr-sdk` and follow the instructions for inclusion in your build tool.

## Documentation

The API documentation is published with each release
at [https://cashapp.github.io/nostrino](https://cashapp.github.io/nostrino)

### Supported NIPs

| Supported | NIP                                                                                                                              |
|:---------:|----------------------------------------------------------------------------------------------------------------------------------|
| ✅        | [01 - Basic protocol flow description](https://github.com/nostr-protocol/nips/blob/master/01.md)                                 |
| 🚧        | [02 - Contact List and Petnames](https://github.com/nostr-protocol/nips/blob/master/02.md)                                       |
| 🗅         | [03 - OpenTimestamps Attestations for Events](https://github.com/nostr-protocol/nips/blob/master/03.md)                          |
| ✅        | [04 - Encrypted Direct Message](https://github.com/nostr-protocol/nips/blob/master/04.md)                                        |
| 🗅         | [05 - Mapping Nostr keys to DNS-based internet identifiers](https://github.com/nostr-protocol/nips/blob/master/05.md)            |
| 🗅         | [06 - Basic key derivation from mnemonic seed phrase](https://github.com/nostr-protocol/nips/blob/master/06.md)                  |
| ✅        | [09 - Event Deletion](https://github.com/nostr-protocol/nips/blob/master/09.md)                                                  |
| 🗅         | [10 - Conventions for clients' use of `e` and `p` tags in text events](https://github.com/nostr-protocol/nips/blob/master/10.md) |
| 🗅         | [11 - Relay Information Document](https://github.com/nostr-protocol/nips/blob/master/11.md)                                      |
| 🗅         | [12 - Generic Tag Queries](https://github.com/nostr-protocol/nips/blob/master/12.md)                                             |
| 🗅         | [13 - Proof of Work](https://github.com/nostr-protocol/nips/blob/master/13.md)                                                   |
| 🗅         | [14 - Subject tag in text events](https://github.com/nostr-protocol/nips/blob/master/14.md)                                      |
| ✅        | [15 - End of Stored Events Notice](https://github.com/nostr-protocol/nips/blob/master/15.md)                                     |
| 🗅         | [16 - Event Treatment](https://github.com/nostr-protocol/nips/blob/master/16.md)                                                 |
| 🗅         | [18 - Reposts](https://github.com/nostr-protocol/nips/blob/master/18.md)                                                         |
| ✅        | [19 - bech32-encoded entities](https://github.com/nostr-protocol/nips/blob/master/19.md)                                         |
| ✅        | [20 - Command Results](https://github.com/nostr-protocol/nips/blob/master/20.md)                                                 |
| 🗅         | [23 - Long-form Content](https://github.com/nostr-protocol/nips/blob/master/23.md)                                               |
| ✅        | [25 - Reactions](https://github.com/nostr-protocol/nips/blob/master/25.md)                                                       |
| 🗅         | [26 - Delegated Event Signing](https://github.com/nostr-protocol/nips/blob/master/26.md)                                         |
| 🗅         | [28 - Public Chat](https://github.com/nostr-protocol/nips/blob/master/28.md)                                                     |
| 🚧        | [29 - Relay-based Groups](https://github.com/nostr-protocol/nips/blob/master/29.md)                                              |
| 🗅         | [33 - Parameterized Replaceable Events](https://github.com/nostr-protocol/nips/blob/master/33.md)                                |
| 🗅         | [36 - Sensitive Content](https://github.com/nostr-protocol/nips/blob/master/36.md)                                               |
| 🗅         | [40 - Expiration Timestamp](https://github.com/nostr-protocol/nips/blob/master/40.md)                                            |
| 🗅         | [42 - Authentication of clients to relays](https://github.com/nostr-protocol/nips/blob/master/42.md)                             |
| ✅        | [44 - Versioned Encrypted Direct Messages](https://github.com/nostr-protocol/nips/blob/master/44.md)                             |
| 🗅         | [46 - Nostr Connect](https://github.com/nostr-protocol/nips/blob/master/46.md)                                                   |
| 🗅         | [50 - Keywords filter](https://github.com/nostr-protocol/nips/blob/master/50.md)                                                 |
| 🗅         | [56 - Reporting](https://github.com/nostr-protocol/nips/blob/master/56.md)                                                       |
| 🗅         | [65 - Relay List Metadata](https://github.com/nostr-protocol/nips/blob/master/65.md)                                             |
| ✅        | [57 - Lightning Zaps](https://github.com/nostr-protocol/nips/blob/master/57.md)                                                  |
| ✅        | [98 - HTTP Auth](https://github.com/nostr-protocol/nips/blob/master/98.md)                                                       |


## Building

> ℹ️ Nostrino uses [Hermit](https://cashapp.github.io/hermit/).
>
>>   Hermit ensures that your team, your contributors, and your CI have the same consistent tooling. Here are the [installation instructions](https://cashapp.github.io/hermit/usage/get-started/#installing-hermit).
>> 
>> [Activate Hermit](https://cashapp.github.io/hermit/usage/get-started/#activating-an-environment) either
by [enabling the shell hooks](https://cashapp.github.io/hermit/usage/shell/) (one-time only, recommended) or manually
sourcing the env with `. ./bin/activate-hermit`.

Use gradle to run all tests

```shell
gradle build
```

## Changelog

See a list of changes in each release in the [CHANGELOG](CHANGELOG.md).

## Contributing

For details on contributing, see the [CONTRIBUTING](CONTRIBUTING.md) guide.
