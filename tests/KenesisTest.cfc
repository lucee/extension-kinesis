component extends="tests.testcases.BaseDistroKidTest"{
	/*********************************** BDD SUITES ***********************************/

	function run( testResults, testBox ){
		// all your suites go here.
		describe( "Check the Kenesis extension", function(){


			it( "Check creating instances from classes from kenesis extension main jar", function() {
				
				// load classes from kenesis.jar
				createObject("java","software.amazon.awssdk.services.kinesis.KinesisAsyncClient","kinesis.extension");
				createObject("java","software.amazon.awssdk.http.apache.internal.utils.ApacheUtils","kinesis.extension");
				
				// load classes from other jars
				createObject("java","io.netty.buffer.ByteBuf","kinesis.extension");
				createObject("java","io.netty.handler.codec.CodecException","kinesis.extension");
				createObject("java","io.netty.handler.address.ResolveAddressHandler","kinesis.extension");
				createObject("java","io.netty.handler.codec.http2.Http2Headers","kinesis.extension");
				createObject("java","io.netty.handler.codec.http.Cookie","kinesis.extension");
				createObject("java","io.netty.util.CharsetUtil","kinesis.extension");
				createObject("java","io.netty.resolver.HostsFileParser","kinesis.extension");
				createObject("java","io.netty.channel.Channel","kinesis.extension");
				createObject("java","io.netty.channel.epoll.EpollMode","kinesis.extension");
				createObject("java","io.netty.channel.unix.FileDescriptor","kinesis.extension");
				createObject("java","software.amazon.awssdk.http.apache.internal.utils.ApacheUtils","kinesis.extension");

			} );

			it( "Check creating instances from other jars directly", function() {
				
				// load classes from other jars
				createObject("java","io.netty.buffer.ByteBuf","org.lucee.netty-buffer");
				createObject("java","io.netty.handler.codec.CodecException","org.lucee.netty-codec");
				createObject("java","io.netty.handler.address.ResolveAddressHandler","org.lucee.netty-handler");
				createObject("java","io.netty.handler.codec.http2.Http2Headers","io.netty.codec-http2");
				createObject("java","io.netty.handler.codec.http.Cookie","io.netty.codec-http");
				createObject("java","io.netty.util.CharsetUtil","io.netty.common");
				createObject("java","io.netty.resolver.HostsFileParser","io.netty.resolver");
				createObject("java","io.netty.channel.Channel","io.netty.transport");
				createObject("java","io.netty.channel.epoll.EpollMode","io.netty.transport-classes-epoll");
				createObject("java","io.netty.channel.unix.FileDescriptor","io.netty.transport-native-unix-common");
				createObject("java","software.amazon.awssdk.http.apache.internal.utils.ApacheUtils","org.lucee.kinesis-all");
			} );

			it( "set record with kenesisPut single record", function() localmode=true {
				var streamName   = "kds-dk-logs-dev";
				var rsp=kinesisPut(
					data:createRecord(),
					partitionKey:"pk-1",
					streamName:streamName
				);
				debug(rsp);
				expect(structKeyExists(rsp, "sequenceNumber")).toBe( 1 );
			} );


			it( "set record with kenesisPut multiple records (3) at once", function() localmode=true {
				var streamName   = "kds-dk-logs-dev";
				var rsp=kinesisPut(
					data:[createRecord(),createRecord(),createRecord()],
					partitionKey:"pk-1",
					streamName:streamName
				);
				debug(rsp);
				expect(structKeyExists(rsp, "sequenceNumber")).toBe( 1 );
			} );

			it( "set record with kenesisPut single record in parallel ", function() localmode=true {
				var streamName   = "kds-dk-logs-dev";
				var key="kp:"&createUniqueID();
				var rsp=kinesisPut(
					data:createRecord(),
					partitionKey:"pk-1",
					streamName:streamName,
					parallel:true,
					// listener can be a component or a struct containing "onSuccess" and "onError"
					"listener": {
						"onSuccess": function(result){
							application[key]=arguments.result;
						}
					}
				);

				// we take a nap because the put is executed in a parallel thread asyncronus
				var count=100;
				while((--count)>0) {
					sleep(100);
					if(structKeyExists(application, key)) break;
				}
				
				if(!structKeyExists(application, key)) throw "parallel thread has written no result to the application scope!";
				
				var rsp=application[key];
				debug(rsp);
				expect(structKeyExists(rsp, "sequenceNumber")).toBe( 1 );
			} );



			it( "kenesisPut load test doing 1000 put", function() localmode=true {
				var messurments=[
					slowest:0,
					fastest:10000000000,
					total:0,
					average:0
				];
				var record=createRecord();
				var times=url.times?:1000;
				loop times=times {
					var start=getTickCount();
					kinesisPut(
						data:record,
						partitionKey:"pk-1",
						streamName:"kds-dk-logs-dev"
					);
					var time=getTickCount()-start;
					messurments.total+=time;
					
					// slowest
					if(messurments.slowest<time) {
						messurments.slowest=time;
					}
					// fastest
					if(messurments.fastest>time) {
						messurments.fastest=time;
					}
				}
				messurments.average=int(messurments.total/times);

				
				debug(messurments);
			} );

			

			it("fetch records from stream with kenesisGet with shardId from last put and iteratorType=TRIM_HORIZON", function() localMode = true {
				streamName = "kds-dk-logs-dev";
				var rsp = kinesisPut(
					data:createRecord(), 
					partitionKey:"pk-1",
					streamName:streamName
				);
				var rsp = kinesisGet(
					streamName:streamName,
					shardId:rsp.shardId,
					iteratorType:"TRIM_HORIZON",
					maxrows:1
				);
				debug(rsp);
				expect( rsp.recordcount ).toBe( 1 );
			});

			it("fetch records from stream with kenesisGet with explicit and timestamp shardId from last put", function() localMode = true {
				streamName = "kds-dk-logs-dev";
				var rsp = kinesisPut(
					data:createRecord(), 
					partitionKey:"pk-1",
					streamName:streamName
				);
				var rsp = kinesisGet(
					streamName:streamName,
					shardId:rsp.shardId,
					timestamp:dateAdd("h", -1, now()),
					iteratorType:"AT_TIMESTAMP",
					maxrows:1
				);
				debug(rsp);
				expect( rsp.recordcount ).toBe( 1 );
			});

			it("fetch records from stream with kenesisGet with explicit and sequenceNumber shardId from last put with AT_SEQUENCE_NUMBER", function() localMode = true {
				streamName = "kds-dk-logs-dev";
				var rsp = kinesisPut(
					data:createRecord(), 
					partitionKey:"pk-1",
					streamName:streamName
				);
				var rsp = kinesisGet(
					streamName:streamName,
					shardId:rsp.shardId,
					sequenceNumber:rsp.sequenceNumber,
					iteratorType:"AT_SEQUENCE_NUMBER"
				);
				debug(rsp);
				expect( rsp.recordcount ).toBe( 1 );
			});
			


			it("fetch records from stream with kenesisGet with explicit and sequenceNumber shardId from last put with AFTER_SEQUENCE_NUMBER", function() localMode = true {
				streamName = "kds-dk-logs-dev";
				var rsp = kinesisPut(
					data:createRecord(), 
					partitionKey:"pk-1",
					streamName:streamName
				);

				kinesisPut(
					data:createRecord(), 
					partitionKey:"pk-1",
					streamName:streamName
				);
				kinesisPut(
					data:createRecord(), 
					partitionKey:"pk-1",
					streamName:streamName
				);



				var rsp2 = kinesisGet(
					streamName:streamName,
					shardId:rsp.shardId,
					sequenceNumber:rsp.sequenceNumber,
					iteratorType:"AFTER_SEQUENCE_NUMBER"
				);
				debug(rsp.sequenceNumber);
				debug(rsp2);
				expect( rsp2.recordcount ).toBe( 3 );
			});
			
			

			it("fetch records from stream with kenesisGet with implicit shardId (not provided)", function() localMode = true {
				
				streamName = "kds-dk-logs-dev";
				
				// we still do a put to make sure there are records
				var rsp = kinesisPut(
					data:createRecord(), 
					partitionKey:"pk-1",
					streamName:streamName
				);
				// we do not provide shardId, then the function simply get the latest
				var rsp = kinesisGet(
					streamName:streamName,
					maxrows:1
				);
				debug(rsp);
				expect( rsp.recordcount ).toBe( 1 );
			});
		} );
	}

	private function createRecord() {
		return {
			"source": "lucee-web01.distrokid.com",
			"type": "lyrics-ds",
			"detail": {
				"metadata": {
					"key": createUUID(),
					"ts" : dateTimeFormat(now(), "iso8601"),
					"datehour": dateTimeFormat(now(), "yyyy-mm-dd HH:nn:ss")
				},
				"data": {
					"user_id": randRange(1,10000000),
					"albumuuid": "uuid-test",
					"album_id": randRange(1,10000000),
					"song_id": randRange(1,10000000),
					"deleted": 0,
					"action": "done",
					"notes": "test",
					"lnotes": {
						"key": "val"
					}
				}
			}
		};
	}
}