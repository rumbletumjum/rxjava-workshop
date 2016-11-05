package com.nurkiewicz.rxjava;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.nurkiewicz.rxjava.util.InfiniteReader;
import com.nurkiewicz.rxjava.util.NumberSupplier;
import com.nurkiewicz.rxjava.util.Sleeper;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Ignore
public class R70_Backpressure {
	
	private static final Logger log = LoggerFactory.getLogger(R70_Backpressure.class);
	
	private Slf4jReporter reporter;
	private Counter counter;
	private MetricRegistry metricRegistry;
	
	@Before
	public void setupMetrics() {
		metricRegistry = new MetricRegistry();
		reporter = Slf4jReporter
				.forRegistry(metricRegistry)
				.outputTo(LoggerFactory.getLogger(R70_Backpressure.class))
				.build();
		reporter.start(1, TimeUnit.SECONDS);
		counter = new Counter();
		metricRegistry.register("counter", counter);
	}
	
	@After
	public void cleanupMetrics() {
		reporter.close();
		metricRegistry.remove("counter");
	}
	
	
	@Test
	public void missingBackpressure() throws Exception {
		Observable
				.interval(5, TimeUnit.MILLISECONDS)
				.doOnNext(x -> log.trace("Emitted: {}", x))
				.observeOn(Schedulers.computation())
				.doOnNext(x -> log.trace("Handling: {}", x))
				.subscribe(x -> Sleeper.sleep(Duration.ofMillis(6)));
		
		TimeUnit.SECONDS.sleep(30);
	}
	
	@Test
	public void loadingDataFromInfiniteReder() throws Exception {
		//given
		Observable<String> numbers = Observable.create(sub -> pushNumbersToSubscriber(sub));

		//when
		final TestObserver<String> subscriber = numbers
				.take(4)
				.test();
		
		//then
		subscriber.assertValues("0", "1", "2", "3");
	}
	
	@Test
	public void backpressureIsNotAproblemIfTheSameThread() throws Exception {
		Observable<String> numbers = Observable.create(sub -> pushNumbersToSubscriber(sub));
		
		numbers
				.doOnNext(x -> log.info("Emitted: {}", x))
				.subscribe(x -> Sleeper.sleep(Duration.ofMillis(6)));
	}
	
	private void pushNumbersToSubscriber(ObservableEmitter<? super String> sub) {
		try (Reader reader = new InfiniteReader(NumberSupplier.lines())) {
			BufferedReader lines = new BufferedReader(reader);
			while (!sub.isDisposed()) {
				sub.onNext(lines.readLine());
			}
		} catch (IOException e) {
			sub.onError(e);
		}
	}
	
	/**
	 * TODO Reimplement `numbers` so that lines are pulled by subscriber, not pushed to subscriber
	 * TODO Create metric showing the difference between the number of generated and consumed events
	 */
	@Test
	public void missingBackpressureIfCrossingThreads() throws Exception {
		Observable<String> numbers = Observable.create(sub -> pushNumbersToSubscriber(sub));
		
		numbers
				.observeOn(Schedulers.io())
				.doOnNext(x -> counter.dec())
				.blockingSubscribe(x -> Sleeper.sleep(Duration.ofMillis(6)));
	}
	
}
