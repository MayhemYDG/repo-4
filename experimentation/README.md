# Experimentation

## Introduction

This module provides an SDK for interfacing with the Automattic-internal A/B testing service, ExPlat.

## Including the SDK

To include the ExPlat SDK in your project, add the following dependency to your build configuration:

```groovy
dependencies {
    implementation 'com.automattic.tracks:experimentation:<recent_release_version_here>'
}
```

## Usage

### Initialization

To begin using the SDK, initialize the `VariationsRepository` with a set of experiments:

```kotlin
val experiments = setOf(
    Experiment("experiment_1"),
    Experiment("experiment_2")
)

// If using a DI container, ensure the repository is scoped as a singleton
val repository: VariationsRepository = VariationsRepository.create(
    experiments = experiments
    // Refer to KDoc for a detailed description of the other parameters
)

// New variations will be reachable during the next application session.
// For more information, check the KDoc for `VariationsRepository#getVariation`
repository.initialize(anonymousId = "currently_logged_in_user_id_or_random_uuid")
```

### Getting variation

Retrieve the specific variation for an experiment as follows:

```kotlin
val variation = repository.getVariation("experiment_1")

when (variation) {
    is Control -> {
        // Proceed with the control group behavior
    }
    is Treatment -> {
        println("Treatment group: ${variation.value}")
        // Implement the treatment variation
    }
}
```

### Handling user logs out

Upon user logout, clear the repository like this:

```kotlin
repository.clear()
```

### Different user logs in

When a different user logs in, re-initialize the repository with the new user's ID:

```kotlin
repository.initialize(anonymousId = "new_user_id")
```

## Testing

The SDK provides a `VariationsRepository` interface for facilitating testing. This interface can be mocked or stubbed as required for unit or integration tests.
