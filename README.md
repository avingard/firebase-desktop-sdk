# Firebase SDK for Desktop environments

**All credit goes to the [official Firebase project](https://firebase.google.com)**

A light wrapper around the public firebase REST / gRPC apis, using kotlin coroutines.

Inspiration: With Kotlin-Multiplatform and Compose Desktop becoming more popular,
I found myself in great need of a firestore desktop implementation
and since there was no such thing, other than the official Admin Sdk, 
I was forced to use the public REST & gRPC apis, and implement one myself.


## Supported libraries so far: 

Firebase auth, Firestore, Firestorage


## Init 

```
Firebase.initializeApp(firebaseOptions {
    apiKey = "your-api-key"
    projectId = "project-id"
    
    //optional
    storageBucket = "bucket-name"
})
```


## Firebase auth

It is integrated with all the currently supported libraries, when current user's not set, 
no auth headers are being sent towards google's servers.


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
but it's a good start nonetheless. The most important ones come to mind: 

- Any sort of disk cache
- Firestore pagination
- A lot of other libraries that you would usually find in the official mobile - web sdk.

And the list goes on...

Any contribution is appreciated!




