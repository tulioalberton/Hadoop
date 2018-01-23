package bigdata;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.time.Duration;
import java.util.DoubleSummaryStatistics;
import java.util.Objects;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

//0,
//+ZcrOp5/c/fJ6mVgP5qMZlOAGDwyjaaDNM0WoWOt2IDb47gT0UwK9lFwkPQv3C7Q,
//2.052803,
//3.911587,
//2.86979

public class UsageDays {

	public static class MyMapper extends Mapper<Object, Text, IntWritable, MyOutput> {

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

			String line = value.toString();
			StringTokenizer tokenizer = new StringTokenizer(line, ",");

			String timestamp = tokenizer.nextToken();
			String vmId = tokenizer.nextToken();
			double minCPU = Double.parseDouble(tokenizer.nextToken());
			double maxCPU = Double.parseDouble(tokenizer.nextToken());
			double avgCPU = Double.parseDouble(tokenizer.nextToken());

			Duration one_hour = Duration.ofHours(1);
			long timeStamp = Long.parseLong(timestamp);
			int hourBucket = (int) (timeStamp / one_hour.getSeconds());

			context.write(new IntWritable(hourBucket), new MyOutput(minCPU, maxCPU, avgCPU));
		}
	}

	public static class MyReducer extends Reducer<IntWritable, MyOutput, IntWritable, MyOutput> {

		@Override
		protected void reduce(IntWritable hourBucket, Iterable<MyOutput> myOutputs, Context context)
				throws IOException, InterruptedException {

			DoubleSummaryStatistics minStats = new DoubleSummaryStatistics();
			DoubleSummaryStatistics maxStats = new DoubleSummaryStatistics();
			DoubleSummaryStatistics avgStats = new DoubleSummaryStatistics();
			
			for (MyOutput out : myOutputs) {
				minStats.accept(out.minCPU);
				maxStats.accept(out.maxCPU);
				avgStats.accept(out.avgCPU);
			}
			
			MyOutput finalOutput = new MyOutput(minStats.getAverage(), maxStats.getAverage(), avgStats.getAverage());
			context.write(hourBucket, finalOutput);
		}
	}
	
	/*
	public static class MyMapper extends Mapper<Object, Text, IntWritable, DoubleWritable> {

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

			String line = value.toString();
			StringTokenizer tokenizer = new StringTokenizer(line, ",");

			String timestamp = tokenizer.nextToken();
			String vmId = tokenizer.nextToken();
			String minCPU = tokenizer.nextToken();
			String maxCPU = tokenizer.nextToken();
			double avgCPU = Double.parseDouble(tokenizer.nextToken());

			Duration one_hour = Duration.ofHours(1);
			long timeStamp = Long.parseLong(timestamp);
			int hourBucket = (int) (timeStamp / one_hour.getSeconds());
			// System.out.println(" Hour Bucket: " + hourBucket + " Timestamp: "+
			// timestamp);

			context.write(new IntWritable(hourBucket), new DoubleWritable(avgCPU));
			// System.out.println(" Hour Bucket: " + hourBucket + "; Timestamp: " +
			// timestamp + "; AvgCPU:" + avgCPU);
		}
	}*/
	
	/*public static class MyReducer extends Reducer<IntWritable, DoubleWritable, IntWritable, DoubleWritable> {

		@Override
		protected void reduce(IntWritable hourBucket, Iterable<DoubleWritable> avgCPUs, Context context)
				throws IOException, InterruptedException {

			double combinedAVGCPU = toStream(avgCPUs).summaryStatistics().getAverage();
			context.write(hourBucket, new DoubleWritable(combinedAVGCPU));
		}

		private static DoubleStream toStream(Iterable<DoubleWritable> avgCPUs) {

			return StreamSupport.stream(avgCPUs.spliterator(), false).mapToDouble(d -> d.get());
		}
	}*/

	private static final class MyOutput implements Writable {
		private double minCPU;
		private double maxCPU;
		private double avgCPU;

		public static MyOutput read(DataInput in) throws IOException {
			MyOutput out = new MyOutput();
			out.readFields(in);
			return out;
		}

		public MyOutput() {
			this.minCPU = 0;
			this.maxCPU = 0;
			this.avgCPU = 0;
		}

		public MyOutput(double minCPU, double maxCPU, double avgCPU) {
			this.minCPU = minCPU;
			this.maxCPU = maxCPU;
			this.avgCPU = avgCPU;
		}
		
		@Override
		public void readFields(DataInput in) throws IOException {
			this.minCPU = in.readDouble();
			this.maxCPU = in.readDouble();
			this.avgCPU = in.readDouble();
		}

		@Override
		public void write(DataOutput out) throws IOException {
			out.writeDouble(minCPU);
			out.writeDouble(maxCPU);
			out.writeDouble(avgCPU);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MyOutput) {
				MyOutput other = (MyOutput) obj;
				return this.minCPU == other.minCPU && this.maxCPU == other.maxCPU && this.avgCPU == other.avgCPU;
			}

			return false;
		}

		@Override
		public int hashCode() {

			return Objects.hash(minCPU, maxCPU, avgCPU);
		}

		@Override
		public String toString() {
			return String.format("min=%.3f; max=%.3f; avg=%.3f", minCPU, maxCPU, avgCPU);
		}
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf, "usageDays");
		job.setJarByClass(UsageDays.class);
		job.setMapperClass(MyMapper.class);
		job.setCombinerClass(MyReducer.class);
		job.setReducerClass(MyReducer.class);
		job.setOutputKeyClass(IntWritable.class);
		job.setOutputValueClass(MyOutput.class);
		//job.setOutputValueClass(DoubleWritable.class);// used with commented code
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
