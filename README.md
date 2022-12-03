# Firebase SDK for Desktop environments

**All credit goes to the [official Firebase project](https://firebase.google.com)**

A light wrapper around the public firebase REST / gRPC apis

Inspiration: With Kotlin-Multiplatform and Compose Desktop becoming more popular,
I found myself in great need of a firestore desktop implementation
and since there was no such thing, other than the official Admin Sdk, 
I was forced to use the public REST & gRPC apis, and implement one myself.


Init: 

```
Firebase.initializeApp(firebaseOptions {
    apiKey = "your-api-key"
    projectId = "project-id"
    
    //optional
    storageBucket = "bucket-name"
})
```

## Firestore



###### CRUD operations: 

```
val firestore = Firebase.firestore

firestore.collection("cities").add(City(name = "New York"))
firestore.collection("cities").document("id").delete()
firestore.collection("cities").document("id").update("address", "myaddress")
firestore.collection("cities").document("id").await()
˙``


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





