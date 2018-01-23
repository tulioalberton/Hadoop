package bigdata;

import java.io.IOException;
import java.time.Duration;
import java.util.StringTokenizer;
import java.util.stream.DoubleStream;
import java.util.stream.StreamSupport;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;


public class DaysAverage {

	public static class MyMapper extends Mapper<Object, Text, IntWritable, DoubleWritable> {

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

			String line = value.toString();
			StringTokenizer tokenizer = new StringTokenizer(line, ",");

			long timeStamp = Long.parseLong(tokenizer.nextToken());
			String vmId = tokenizer.nextToken();
			String minCPU = tokenizer.nextToken();
			String maxCPU = tokenizer.nextToken();
			double avgCPU = Double.parseDouble(tokenizer.nextToken());
			
			Duration one_day = Duration.ofDays(1);
			int dayBucket = (int) (timeStamp / one_day.getSeconds());
			int day = (dayBucket);
			
			context.write(new IntWritable(day), new DoubleWritable(avgCPU));
		}
	}

	public static class MyReducer extends Reducer<IntWritable, DoubleWritable, IntWritable, DoubleWritable> {

		@Override
		protected void reduce(IntWritable dayBucket, Iterable<DoubleWritable> avgCPUs, Context context)
				throws IOException, InterruptedException {

			double combinedAVGCPU = toStream(avgCPUs).summaryStatistics().getAverage();
			context.write(dayBucket, new DoubleWritable(combinedAVGCPU));
		}

		private static DoubleStream toStream(Iterable<DoubleWritable> avgCPUs) {

			return StreamSupport.stream(avgCPUs.spliterator(), false).mapToDouble(d -> d.get());
		}
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf, "DaysAverage");
		job.setJarByClass(UsageDays.class);
		job.setMapperClass(MyMapper.class);
		job.setCombinerClass(MyReducer.class);
		job.setReducerClass(MyReducer.class);
		job.setOutputKeyClass(IntWritable.class);
		job.setOutputValueClass(DoubleWritable.class);// used with commented code
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}

}
