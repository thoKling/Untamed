# The legitimate Microsoft sign-in flow

*First-task deliverable 3, and the specification milestone 4 was built to. The
chain below is implemented against the real endpoints; this document is now both
the reason for it and its description.*

## The rules this flow exists to satisfy

- The launcher **never** asks for a Microsoft password, a Minecraft password, or
  a manually pasted access token.
- The launcher **never** implements or offers an authentication bypass, an
  offline account, or a cracked mode.
- The user must own Minecraft: Java Edition, and ownership is checked against
  Mojang's own entitlement endpoint, not inferred.

Sign-in happens on Microsoft's own page in the user's own browser. The launcher
never renders a login form and never sees what is typed into one.

## Prerequisite: an Azure application registration

The Minecraft services API only answers clients whose Azure AD application has
been approved by Mojang for use with the Minecraft launcher API. This is an
application step with a lead time, not a code step, which is why the code was
written to it rather than waiting for it. The registration needs:

- a public client, no client secret, since a desktop app cannot keep one;
- the delegated scope `XboxLive.signin offline_access`;
- a loopback redirect URI, `http://localhost:<port>/`, which is the
  Microsoft-recommended redirect for native applications.

The code is written to that registration rather than waiting for it. The client
id is the one value a build has to supply, read from the
`SURVIVAL_OVERHAUL_CLIENT_ID` environment variable or from a constant compiled
into the build. When it is absent, "not configured" is a state of its own with
its own sentence, so the user is told that this build has no registration yet
rather than watching a sign-in fail halfway along the chain.

A registration now exists, `b7fa41f7-064d-4a19-9ffb-06d49731276b`, and it is
compiled into the build. Mojang's approval of it for the Minecraft services API
is a separate step and is still outstanding, so the chain is expected to run
through Microsoft, Xbox Live and XSTS and then be refused at
`login_with_xbox` with HTTP 403 until that lands.

The redirect port is chosen by the operating system at each sign-in, which is
what the Microsoft registration for a native client expects: any loopback port is
accepted, so two launchers signing in at once never fight over a fixed one.

## The chain

**1. Microsoft OAuth 2.0, authorization code with PKCE.**
The launcher opens the system browser at the Microsoft authorize endpoint and
listens on a loopback socket for the redirect. PKCE is what makes a secretless
public client safe. Result: a Microsoft access token and a refresh token.

The `state` parameter is random per sign-in and is checked before anything else
in the redirect is read, including before an error is read: a response that does
not match the request this launcher made is not this launcher's sign-in, and the
only safe thing to do with it is stop. A repeated parameter keeps its first
value, since nothing in the redirect is a list and a duplicate is the shape of an
attempt to slip a second value past a check that read the first.

The listener is bound to the loopback address on a port the operating system
chooses, exists for the length of one sign-in, and answers with a page carrying
no script, no stylesheet and no image. It is the one plain-HTTP address in the
launcher and not an exception to the HTTPS rule: the traffic is a browser on this
machine talking to this process and never reaches a network.

**2. Xbox Live.** `POST https://user.auth.xboxlive.com/user/authenticate` with
the Microsoft token. Result: an Xbox Live token and a user hash.

**3. XSTS.** `POST https://xsts.auth.xboxlive.com/xsts/authorize` for the
`rp://api.minecraftservices.com/` relying party. Result: an XSTS token. Two
failures here are ordinary and need real messages rather than a stack trace:
no Xbox account attached to the Microsoft account, and a child account that must
be added to a family. Six XSTS refusal codes are translated into sentences the
user can act on, and the code itself is shown only for one the launcher does not
recognise, because that is the only case where the number is the useful part.

**4. Minecraft services.**
`POST https://api.minecraftservices.com/authentication/login_with_xbox` with the
XSTS token and user hash. Result: the Minecraft access token.

**5. Ownership and profile.**
`GET /entitlements/mcstore` establishes that the account owns Java Edition, and
`GET /minecraft/profile` returns the UUID and name the game is launched with. An
account with no profile is one that has never set up Java Edition; that is a
distinct message from "you do not own the game", and it is the 404 from the
profile endpoint that distinguishes them.

Ownership is "the entitlement list is not empty" rather than a match against a
set of product names. Java Edition arrives under several names depending on how
it was bought, Game Pass adds its own, and Mojang adds more when a new way to own
the game appears. An account that owns nothing gets an empty list, which is the
case this check exists to catch, and a launcher that refuses a legitimately owned
copy because the product name is new is worse than one that trusts Mojang's own
answer.

Every request is HTTPS. There is no non-TLS fallback anywhere in the chain.

## Token handling

| Value | Lifetime | Where it goes |
| --- | --- | --- |
| Microsoft refresh token | long | OS credential store where one exists; that is the only value worth persisting |
| Microsoft / Xbox / XSTS tokens | minutes | memory only, discarded as soon as the next step consumes them |
| Minecraft access token | ~24h | memory, re-acquired on start from the refresh token |

Nothing above is ever written to the launcher log. `LauncherLog` says so in its
own class comment, and the diagnostics report is built from settings, manifest
origin, installation snapshot, log lines and the command the game was started
with. That command is redacted by value before anything but the process sees it:
the access token and the XUID are replaced wherever they appear, however they
were embedded. What survives is the player name and the account UUID, which
every server the player joins already knows, and that is the whole of the
account data "Copy diagnostics" can put into a support thread.

If no OS credential store is available, the fallback is to keep the refresh
token in memory only and ask the user to sign in again next launch. Writing it
to a plaintext file is not one of the options.

**That fallback is what the launcher currently does.** Nothing in the Java
standard library reaches the Windows Credential Manager, the macOS Keychain or
the Secret Service on Linux, so binding to one of them means taking on a native
dependency, which is a decision to make deliberately rather than one to slip into
a milestone. Until then the token lives in the process and dies with it. That
costs a browser round trip per launch, and the account settings say so in the
user's own words rather than leaving them to discover it.

Keeping a token out of the log is enforced at the type level as well as by
discipline: every wire type that carries one overrides `toString`, and the HTTP
response type prints only the length of its body. A data class that prints its
fields is one careless log line away from writing a credential to a file.

## What the UI shows

The top bar carries the account: the player name when there is a session, the
reason the session itself gives when there is not, and the current step of the
sign-in while one is running. The six services each answer in turn and any of
them can be the slow one, so the user is told which is being waited on rather
than watching one unexplained spinner. The same control is Cancel while a
sign-in is running, Sign out when there is a session, and Sign in otherwise.

The settings screen repeats it as an account section, because that is where
someone looks to find out what the launcher keeps. It carries the two sentences
the user is owed: that the password is typed on Microsoft's page and not this
one, and what becomes of the sign-in when the launcher closes.

The Play button is disabled by the session and by nothing else. Its hint on the
home screen is the same reason. So the launcher can install the whole product,
resolve the version documents, expand the argument templates and unpack the
natives, and still not start the game, because the account is the one input it
will not invent. There is deliberately no second implementation of the accounts
port, and the composition root is the only place one could be added.
