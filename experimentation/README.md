# Experimentation

## Introduction

This module provides an SDK to communicate with Automattic-internal A/B testing service: ExPlat.

## Coordinates

```groovy
dependencies {
    implementation 'com.automattic.tracks:experimentation:{recent_release}'
}
```

## Usage

### Initialization

```kotlin
val experiments = setOf(
    Experiment("experiment_1"),
    Experiment("experiment_2")
)

// If injected in a DI container, the repository should be a singleton
val repository: VariationsRepository = VariationsRepository.create(
    experiments = experiments
    /* see KDoc documentation for detailed description of parameters */
)
repository.configure(anonId = "currently_logged_in_user_id_or_random_uuid")
```

### Getting variation

```kotlin
// New variations will be available on the next application session. See KDoc for `VariationsRepository#getVariation`
val variation = repository.getVariation("experiment_1")

when (variation) {
    is Control -> {
        // continue with control group
    }
    is Treatment -> {
        println("Treatment group: ${variation.value}")
        // apply treatment changes
    }
}
```

### User logs out

```kotlin
repository.clean()
```

### Different user logs in

```kotlin
repository.configure(anonId = "new_user_id")
```

## Testing
The SDK offers `VariantionsRepository` interface, which can be doubled in tests.
