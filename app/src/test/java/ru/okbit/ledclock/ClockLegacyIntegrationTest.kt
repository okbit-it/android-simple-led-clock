package ru.okbit.ledclock

import org.robolectric.annotation.Config

/** Robolectric 4.14 dropped API19. Use -PlegacyAndroid to select 4.11.1 for this suite. */
@Config(sdk = [19], qualifiers = "land-hdpi")
class ClockLegacyIntegrationTest : ClockIntegrationTest()
