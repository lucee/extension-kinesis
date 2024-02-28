
# Lucee AWS Kinesis Extension - Function Examples

## kinesisPut Function

The `kinesisPut` function sends data records to an AWS Kinesis stream. It supports the submission of both single and multiple records and offers both synchronous and asynchronous execution modes.

### Example 1: Simple Record Submission

This example demonstrates how to submit a single data record to a Kinesis stream.

```cfc
recordData = { "exampleKey" = "exampleValue" };
streamName = "yourStreamName";
partitionKey = "yourPartitionKey";

// Submit a single record
kinesisPut(data=recordData, streamName=streamName, partitionKey=partitionKey);
```

### Example 2: Batch Record Submission

This example shows how to submit multiple records to a Kinesis stream in a single operation.

```cfc
records = [
    { "exampleKey1" = "exampleValue1" },
    { "exampleKey2" = "exampleValue2" }
];
streamName = "yourStreamName";
partitionKey = "yourPartitionKey";

// Submit multiple records
kinesisPut(data=records, streamName=streamName, partitionKey=partitionKey);
```

### Example 3: Asynchronous Submission with Listener

This example illustrates using the `kinesisPut` function in parallel mode with a listener to handle the operation's result asynchronously.

```cfc
records = [
    { "exampleKey1" = "exampleValue1" },
    { "exampleKey2" = "exampleValue2" }
];
streamName = "yourStreamName";
partitionKey = "yourPartitionKey";

// Define a listener (can be a component or a struct with functions like here)
listener = {
    onSuccess = function(result) {
    	// write result to console
    	systemOutput("Record submitted successfully: " & serializeJson(result),true,true);
    },
    onError = function(error) {
        // write result to console
    	systemOutput("Error submitting record: " & serializeJson(error),true,true);
    }
};

// Submit records asynchronously with a listener for handling the result
kinesisPut(data=records, streamName=streamName, partitionKey=partitionKey, parallel=true, listener=listener);
```

In the asynchronous submission example, `listener` is a struct with `onSuccess` and `onError` functions to handle successful submissions and errors, respectively. This also can be a component. This allows for non-blocking operation and result handling in a background process.

Additionally, you have the ability to specify the maximum number of threads that can be executed in parallel by the extension for kinesisPut operations. 
This can be achieved through the system property `lucee.kinesis.maxThreads=10` or the environment variable `LUCEE_KINESIS_MAXTHREADS=10`. 
By default, parallel execution is limited to 10 threads, ensuring efficient resource utilization while maintaining optimal performance.

## kinesisGet Function

Retrieves data records from an AWS Kinesis stream based on the provided criteria, such as stream name, shard ID, and the starting point for fetching records.

### Example Usage:

```cfc
streamName = "yourStreamName";
shardId = "yourShardId";
startingSequenceNumber = "yourStartingSequenceNumber";

// Fetch records from a specified shard
records = kinesisGet(streamName=streamName, shardId=shardId, sequenceNumber=startingSequenceNumber);
dump(records);
```

## kinesisInfo Function

Fetches information about an AWS Kinesis stream or its shards, providing details like the stream's status, shard information, and more.

### Example Usage:

```cfc
streamName = "yourStreamName";

// Retrieve information about the specified stream
streamInfo = kinesisInfo(streamName=streamName);
dump(streamInfo);
```

For more examples, check out the TestBox test cases that are part of this repository.


Issues: https://luceeserver.atlassian.net/issues/?jql=labels%20%3D%20s3

Docs: https://docs.lucee.org/categories/kinesis.html
