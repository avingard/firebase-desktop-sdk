# Firebase SDK for Desktop environments

**All credit goes to the [official Firebase project](https://firebase.google.com)**

A light wrapper around the public firebase REST / gRPC apis, using kotlin coroutines.

## Supported libraries: 
Firebase auth, Firestore, Firestorage


## Init 

```
Firebase.initializeApp(firebaseOptions {
    apiKey = "your-api-key"
    projectId = "project-id"
    
    //optional
    storageBucket = "bucket-name"
    
    // emulators
    firestoreEmulator = emulator {
        useEmulator = true
        address = "localhost"
        port = 8080
    }
    
    firebaseAuthEmulator = emulator {
        useEmulator = true
        address = "localhost"
        port = 9099
    }

    firestorageEmulator = emulator {
        useEmulator = true
        address = "localhost"
        port = 9199
    }
})
```


## Firebase auth

```
val auth = Firebase.auth

auth.signInWithEmailAndPassword("", "")

val user = auth.getCurrentUser()
val flow: StateFlow<FirebaseUser?> = auth.getCurrentUserFlow()

// Listen for authenticated user token updates: 

user?.getRefreshTokenFlow()?.collect { token ->
    // Save token locally        
} 

```


## Firestore



###### CRUD operations: 

```
val firestore = Firebase.firestore

firestore.collection("cities").add(City(name = "New York"))
firestore.collection("cities").document("id").delete()
firestore.collection("cities").document("id").update("address", "myaddress")
firestore.collection("cities").document("id").await()
```


###### Get realtime updates: 

```
Firebase.firestore
    .collection("cities")
    .where(field = "state", equalTo = "CA")
    .snapshots()
    .collect { snapshot: QuerySnapshot ->
        LOG.info("Current cites in CA:  ${snapshot.documents}")
    }
```


## What's missing?

There are many things I did not require for my project. It is far from ready to be used in production,
but it's a good start nonetheless.

Any contribution is appreciated!




