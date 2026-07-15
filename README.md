# Lucee AWS Kinesis Extension - Function Examples

## Credential Configuration

The client attempts to find AWS credentials as follows:

1. **Explicit Credentials**: If `accessKeyId` and `secretAccessKey` are provided via function arguments, these are used.
2. **Application.cfc**: `this.kinesis.accessKeyId`, `this.kinesis.secretAccessKey`, `this.kinesis.host`, `this.kinesis.region`
3. **Environment / system properties**: `LUCEE_KINESIS_ACCESSKEYID`, `LUCEE_KINESIS_SECRETACCESSKEY`, `LUCEE_KINESIS_HOST`, `LUCEE_KINESIS_REGION` (or `lucee.kinesis.*`)
4. **Default Credential Provider Chain**: In absence of explicit credentials, the SDK searches in:
   - Environment Variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
   - Java System Properties (`aws.accessKeyId`, `aws.secretKey`)
   - Credential profiles file (`~/.aws/credentials`)
   - ECS Container Credentials (for applications running on Amazon ECS)
   - EC2 Instance Profile Credentials (for applications running on Amazon EC2)

## HTTP connection pool (DC-40698 / same pattern as S3 LDEV-6373)

One shared `KinesisClient` is reused per credential/host/region (plus pool settings). Concurrent `PutRecord` calls share that client's **AWS SDK Apache HTTP connection pool**. The AWS SDK default is 50 connections; this extension defaults to **128**.

When the pool is saturated, callers can block until `connectionAcquisitionTimeout` and fail with `ConnectionPoolTimeoutException` ("Timeout waiting for connection from pool"). Raising worker/`maxThreads` alone does not fix this — tune the HTTP pool.

### Application.cfc

```cfc
component {
    this.kinesis.accessKeyId = "my-access-key";
    this.kinesis.secretAccessKey = "my-secret-key";
    this.kinesis.host = "localstack:4566";   // optional custom endpoint
    this.kinesis.region = "us-east-1";

    // HTTP connection pool (AWS SDK v2 Apache client, per shared Kinesis client)
    this.kinesis.pool = {
        maxConnections: 200,              // extension default is 128 (AWS SDK default is 50)
        connectionTimeout: 10000,         // ms to wait for a pool slot (SDK connectionAcquisitionTimeout)
        socketTimeout: 50000,             // ms read timeout on an active connection
        connectionMaxIdleMillis: 60000,   // discard idle pooled connections
        warnUtilization: 0.8              // log WARN at 80% utilization; 0 to disable
    };
}
```

### Environment variables / system properties

| Env | System property | Meaning |
| --- | --- | --- |
| `LUCEE_KINESIS_POOL_MAXCONNECTIONS` | `lucee.kinesis.pool.maxconnections` | Max concurrent HTTP connections per Kinesis client (extension default: 128) |
| `LUCEE_KINESIS_POOL_CONNECTIONTIMEOUT` | `lucee.kinesis.pool.connectiontimeout` | Ms to wait for a connection from the pool (acquisition timeout) |
| `LUCEE_KINESIS_POOL_SOCKETTIMEOUT` | `lucee.kinesis.pool.sockettimeout` | Socket read timeout in ms |
| `LUCEE_KINESIS_POOL_CONNECTIONMAXIDLEMILLIS` | `lucee.kinesis.pool.connectionmaxidlemillis` | Idle connection TTL in ms |
| `LUCEE_KINESIS_POOL_WARNUTILIZATION` | `lucee.kinesis.pool.warnutilization` | Log when in-flight requests reach this fraction of `maxConnections` (default: 0.8; `0` disables) |

Alias: `LUCEE_KINESIS_MAXCONNECTIONS` / `lucee.kinesis.maxconnections` also sets max connections if the `pool.*` property is unset.

Under pool pressure the extension logs WARN/ERROR on the `Kinesis` log channel (same idea as the S3 extension pool monitor).


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

Note: `maxThreads` only sizes the optional parallel executor. It does **not** size the AWS HTTP connection pool — use `LUCEE_KINESIS_POOL_MAXCONNECTIONS` for that.

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
