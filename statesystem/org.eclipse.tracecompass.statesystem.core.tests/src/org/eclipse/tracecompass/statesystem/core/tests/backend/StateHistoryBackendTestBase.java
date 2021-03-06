/*******************************************************************************
 * Copyright (c) 2016 Ericsson and others
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.statesystem.core.tests.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.statesystem.core.backend.IStateHistoryBackend;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.interval.TmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

/**
 * Abstract class to test implementations of the {@link IStateHistoryBackend}
 * interface.
 *
 * @author Patrick Tasse
 * @author Geneviève Bastien
 */
public abstract class StateHistoryBackendTestBase {

    /* Some state values of each type */
    private static final ITmfStateValue INT_VAL1 = TmfStateValue.newValueInt(-42);
    private static final ITmfStateValue INT_VAL2 = TmfStateValue.newValueInt(675893);
    private static final ITmfStateValue LONG_VAL1 = TmfStateValue.newValueLong(-78L);
    private static final ITmfStateValue LONG_VAL2 = TmfStateValue.newValueLong(2234L);
    private static final ITmfStateValue DOUBLE_VAL1 = TmfStateValue.newValueDouble(-9.87);
    private static final ITmfStateValue DOUBLE_VAL2 = TmfStateValue.newValueDouble(50324.131643);
    private static final ITmfStateValue STR_VAL1 = TmfStateValue.newValueString("A string");
    private static final ITmfStateValue STR_VAL2 = TmfStateValue.newValueString("Another éèstr");

    /**
     * Gets the backend to be used for building.
     *
     * @param startTime
     *            The start time of the history
     *
     * @return The backend to be used for building.
     * @throws IOException
     *             if an exception occurs
     */
    protected abstract IStateHistoryBackend getBackendForBuilding(long startTime) throws IOException;

    /**
     * Gets the backend to be used for querying. The default implementation
     * returns the backend that was used for building.
     * <p>
     * Only the returned backend should be used after calling this method. The
     * one sent in parameter might have been disposed.
     *
     * @param backend
     *            The backend that was used for building
     * @return The backend to be used for querying.
     * @throws IOException
     *             if an exception occurs
     */
    @SuppressWarnings("unused")
    protected IStateHistoryBackend getBackendForQuerying(IStateHistoryBackend backend) throws IOException {
        return backend;
    }

    /**
     * Prepares a backend to be used in tests. The specified intervals will be
     * inserted in the backend, and then the backend will be closed.
     *
     * @param startTime
     *            The start time of the history
     * @param endTime
     *            The end time at which to close the history
     * @param intervals
     *            The intervals to insert in the history backend
     * @return The backend to be used for querying.
     */
    protected final @Nullable IStateHistoryBackend prepareBackend(long startTime, long endTime,
            List<ITmfStateInterval> intervals) {

        try {
            IStateHistoryBackend backend = getBackendForBuilding(startTime);
            insertIntervals(backend, intervals);
            backend.finishedBuilding(Math.max(endTime, backend.getEndTime()));
            return getBackendForQuerying(backend);
        } catch (IOException e) {
            fail(e.getMessage());
            return null;
        }
    }

    /**
     * Insert the specified intervals in the provided backend.
     *
     * @param backend
     *            The backend to be used
     * @param intervals
     *            The intervals to insert in the history backend
     */
    protected static void insertIntervals(IStateHistoryBackend backend, List<ITmfStateInterval> intervals) {
        for (ITmfStateInterval interval : intervals) {
            backend.insertPastState(interval.getStartTime(), interval.getEndTime(), interval.getAttribute(), interval.getStateValue());
        }
    }

    /**
     * Initializes a list for the number of attributes in the backend and
     * associates a null value for each
     *
     * @param nbAttrib
     *            The number of attributes in the backend
     * @return A list of null values for each attribute
     */
    private static List<@Nullable ITmfStateInterval> prepareIntervalList(int nbAttrib) {
        List<@Nullable ITmfStateInterval> intervals = new ArrayList<>(nbAttrib);
        for (int i = 0; i < nbAttrib; i++) {
            intervals.add(null);
        }
        return intervals;
    }

    /**
     * Test the integrity of a backend by first building the backend with the
     * specified intervals, closing it, and then querying at every single
     * timestamp, making sure that all returned intervals intersect with the
     * query time. The backend start and end time will be checked.
     * <p>
     * If <code>allowNull</code> is false, the specified intervals must cover
     * the full range for all attributes. The method will make sure that no null
     * intervals are returned.
     *
     * @param startTime
     *            The start time of the history
     * @param endTime
     *            The end time of the history
     * @param nbAttr
     *            The number of attributes
     * @param intervals
     *            The list of intervals to insert
     * @param allowNull
     *            True if null intervals are allowed, false otherwise
     * @return The backend to be used for querying.
     */
    protected final IStateHistoryBackend buildAndQueryFullRange(long startTime, long endTime, int nbAttr, List<ITmfStateInterval> intervals, boolean allowNull) {

        final IStateHistoryBackend backend = prepareBackend(startTime, endTime, intervals);
        assertNotNull(backend);

        try {
            /*
             * Query at every valid time stamp, making sure only the expected
             * intervals are returned.
             */
            for (long t = backend.getStartTime(); t <= backend.getEndTime(); t++) {
                List<@Nullable ITmfStateInterval> stateInfo = prepareIntervalList(nbAttr);
                backend.doQuery(stateInfo, t);
                for (int attr = 0; attr < stateInfo.size(); attr++) {
                    ITmfStateInterval interval = stateInfo.get(attr);
                    if (!allowNull) {
                        assertTrue("null interval at t=" + t + " for attr=" + attr, interval != null);
                    }
                    if (interval != null) {
                        assertTrue(interval + " does not intersect t=" + t, interval.intersects(t));
                    }
                }
            }

            assertEquals(startTime, backend.getStartTime());
            assertEquals(endTime, backend.getEndTime());
        } catch (StateSystemDisposedException e) {
            fail(e.getMessage());
        }
        return backend;
    }

    /**
     * Test the full query method by filling a small backend with intervals
     * placed in a "stair-like" fashion, like this:
     *
     * <pre>
     * |x----x----x---x|
     * |xx----x----x--x|
     * |x-x----x----x-x|
     * |x--x----x----xx|
     * |      ...      |
     * </pre>
     *
     * and then querying at every single timestamp, making sure all, and only,
     * the expected intervals are returned.
     */
    @Test
    public void testCascadingIntervals() {
        final int nbAttr = 10;
        final long duration = 10;
        final long startTime = 0;
        final long endTime = 1000;

        List<ITmfStateInterval> intervals = new ArrayList<>();
        for (long t = startTime + 1; t <= endTime + duration; t++) {
            intervals.add(new TmfStateInterval(
                    Math.max(startTime, t - duration),
                    Math.min(endTime, t - 1),
                    (int) t % nbAttr,
                    TmfStateValue.newValueLong(t)));
        }

        buildAndQueryFullRange(startTime, endTime, nbAttr, intervals, false);
    }

    /**
     * Test the full query method by filling a small backend with intervals that
     * take the full time range, like this:
     *
     * <pre>
     * |x-------------x|
     * |x-------------x|
     * |x-------------x|
     * |x-------------x|
     * |      ...      |
     * </pre>
     *
     * and then querying at every single timestamp, making sure all, and only,
     * the expected intervals are returned.
     */
    @Test
    public void testFullIntervals() {
        final int nbAttr = 1000;
        final long startTime = 0;
        final long endTime = 1000;

        List<ITmfStateInterval> intervals = new ArrayList<>();
        for (int attr = 0; attr < nbAttr; attr++) {
            intervals.add(new TmfStateInterval(
                    startTime,
                    endTime,
                    attr,
                    TmfStateValue.newValueLong(attr)));
        }

        buildAndQueryFullRange(startTime, endTime, nbAttr, intervals, false);
    }

    /**
     * Test inserting values of different types and querying them right after
     */
    @Test
    public void testInsertQueryStateValues() {
        /* Test specific data initialization */
        long startTime = 10;
        long timeStep = 5;
        int intQuark = 0;
        int longQuark = 1;
        int doubleQuark = 2;
        int strQuark = 3;

        try {
            IStateHistoryBackend backend = getBackendForBuilding(startTime);
            assertNotNull(backend);

            /* Int interval */
            backend.insertPastState(startTime, startTime + timeStep, intQuark, INT_VAL1);
            ITmfStateInterval interval = backend.doSingularQuery(startTime, intQuark);

            assertEquals("Int interval start time", startTime, interval.getStartTime());
            assertEquals("Int interval end time", startTime + timeStep, interval.getEndTime());
            assertEquals("Int interval value", INT_VAL1, interval.getStateValue());

            /* Long interval */
            backend.insertPastState(startTime, startTime + timeStep, longQuark, LONG_VAL1);
            interval = backend.doSingularQuery(startTime, longQuark);

            assertEquals("Long interval start time", startTime, interval.getStartTime());
            assertEquals("Long interval end time", startTime + timeStep, interval.getEndTime());
            assertEquals("Long interval value", LONG_VAL1, interval.getStateValue());

            /* Double interval */
            backend.insertPastState(startTime, startTime + timeStep, doubleQuark, DOUBLE_VAL1);
            interval = backend.doSingularQuery(startTime, doubleQuark);

            assertEquals("Double interval start time", startTime, interval.getStartTime());
            assertEquals("Double interval end time", startTime + timeStep, interval.getEndTime());
            assertEquals("Double interval value", DOUBLE_VAL1, interval.getStateValue());

            /* String interval */
            backend.insertPastState(startTime, startTime + timeStep, strQuark, STR_VAL1);
            interval = backend.doSingularQuery(startTime, strQuark);

            assertEquals("String interval start time", startTime, interval.getStartTime());
            assertEquals("String interval end time", startTime + timeStep, interval.getEndTime());
            assertEquals("String interval value", STR_VAL1, interval.getStateValue());

            /*
             * Add other intervals for the int quark and query at different
             * times
             */
            backend.insertPastState(startTime + timeStep + 1, startTime + (2 * timeStep), intQuark, INT_VAL2);
            backend.insertPastState(startTime + (2 * timeStep) + 1, startTime + (3 * timeStep), intQuark, INT_VAL1);

            interval = backend.doSingularQuery(startTime + timeStep, intQuark);
            assertEquals("Int interval value", INT_VAL1, interval.getStateValue());

            interval = backend.doSingularQuery(startTime + timeStep + 1, intQuark);
            assertEquals("Int interval value", INT_VAL2, interval.getStateValue());

            interval = backend.doSingularQuery(startTime + (2 * timeStep), intQuark);
            assertEquals("Int interval value", INT_VAL2, interval.getStateValue());

            interval = backend.doSingularQuery(startTime + (2 * timeStep) + 1, intQuark);
            assertEquals("Int interval value", INT_VAL1, interval.getStateValue());

        } catch (TimeRangeException | StateSystemDisposedException | IOException | AttributeNotFoundException e) {
            fail(e.getMessage());
        }
    }

    /**
     * Test querying various state value types after the state system has been
     * built and finished
     */
    @Test
    public void testBuildNowQueryLaterStateValues() {
        /* Test specific data initialization */
        long startTime = 10;
        long timeStep = 5;
        int intQuark = 0;
        int longQuark = 1;
        int doubleQuark = 2;
        int strQuark = 3;
        int nbAttribs = 4;

        try {
            IStateHistoryBackend backend = getBackendForBuilding(startTime);
            assertNotNull(backend);

            long firstEnd = startTime + timeStep;
            long nextStart = firstEnd + 1;
            long endTime = nextStart + timeStep;

            insertIntervals(backend, ImmutableList.of(new TmfStateInterval(startTime, startTime + timeStep, intQuark, INT_VAL1),
                    new TmfStateInterval(startTime, startTime + timeStep, longQuark, LONG_VAL1),
                    new TmfStateInterval(startTime, startTime + timeStep, doubleQuark, DOUBLE_VAL1),
                    new TmfStateInterval(startTime, startTime + timeStep, strQuark, STR_VAL1),
                    new TmfStateInterval(nextStart, endTime, intQuark, INT_VAL2),
                    new TmfStateInterval(nextStart, endTime, longQuark, LONG_VAL2),
                    new TmfStateInterval(nextStart, endTime, doubleQuark, DOUBLE_VAL2),
                    new TmfStateInterval(nextStart, endTime, strQuark, STR_VAL2)));

            backend.finishedBuilding(endTime);

            /* Make sure the end time corresponds to the backend end time */
            assertEquals(endTime, backend.getEndTime());

            IStateHistoryBackend backendQuery = getBackendForQuerying(backend);

            /* Verify start and end times */
            assertEquals("Backend start time", startTime, backendQuery.getStartTime());
            assertEquals("Backend end time", endTime, backendQuery.getEndTime());

            List<@Nullable ITmfStateInterval> intervals = prepareIntervalList(nbAttribs);

            /* Do a full query at start and verify the values */
            backendQuery.doQuery(intervals, startTime);

            ITmfStateInterval interval = intervals.get(intQuark);
            assertNotNull(interval);
            assertEquals("Int value after read", INT_VAL1, interval.getStateValue());
            interval = intervals.get(longQuark);
            assertNotNull(interval);
            assertEquals("Long value after read", LONG_VAL1, interval.getStateValue());
            interval = intervals.get(doubleQuark);
            assertNotNull(interval);
            assertEquals("Double value after read", DOUBLE_VAL1, interval.getStateValue());
            interval = intervals.get(strQuark);
            assertNotNull(interval);
            assertEquals("String value after read", STR_VAL1, interval.getStateValue());

            /* Do a full query at the end and verify the values */
            backendQuery.doQuery(intervals, endTime);

            interval = intervals.get(intQuark);
            assertNotNull(interval);
            assertEquals("Int value after read", INT_VAL2, interval.getStateValue());
            interval = intervals.get(longQuark);
            assertNotNull(interval);
            assertEquals("Long value after read", LONG_VAL2, interval.getStateValue());
            interval = intervals.get(doubleQuark);
            assertNotNull(interval);
            assertEquals("Double value after read", DOUBLE_VAL2, interval.getStateValue());
            interval = intervals.get(strQuark);
            assertNotNull(interval);
            assertEquals("String value after read", STR_VAL2, interval.getStateValue());

        } catch (TimeRangeException | IOException | StateSystemDisposedException e) {
            fail(e.getMessage());
        }
    }
}
