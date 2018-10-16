'''
Runs simulated annealing for parameters in [0, 1]^d, using a random distribution with mean
equal to the current parameter and a given variance to sample neighbors, linear
annealing of the temperature parameter, and the exponential distribution to decide whether
to switch to a new point that is worse than the old one.
Note that the neighbor distribution is nudged away from 0 or 1 toward 0.1 or 0.9 before
sampling randomly.
'''
import random
import time
from simulated_annealing import get_temp, neighbor_truncated_gaussian, should_switch_params

# works with parameter vector scaled to [0, 1] in each dimension.
def find_params_simulated_annealing_fpsb(param_count, max_steps, max_temp,
                                         neighbor_variance, sample_function, should_print,
                                         initial_params, att_mixed_strat):
    '''
    Run simulated annealing in space [0, 1]^param_count, for max_steps trials.
    Temperature will be annealed from max_temp to 0.
    The value of the current parameter vectcor will be generated by calling
    sample_function(cur_neighbor, samples_per_param), which should give the mean result
    of samples_per_param trials with parameter vector cur_neighbor.
    The neighbor of vector saved_params is generated independently for each dimension,
    using a Beta distribution with mean at the current value, and variance
    neighbor_variance.
    If should_print is True, will print out each time the saved_params is altered.
    Returns the final saved parameters and their estimated mean value.
    The opponent (attacker) plays the mixed strategy att_mixed_strat.
    '''
    if param_count < 1:
        raise ValueError("param_count must be >= 1: " + str(param_count))
    if max_steps < 1:
        raise ValueError("max_steps must be >= 1: " + str(max_steps))
    if max_temp <= 0.0:
        raise ValueError("max_temp must be > 0: " + str(max_temp))
    if neighbor_variance <= 0.0 or neighbor_variance >= 1.0:
        raise ValueError("neighbor_variance must be in (0, 1): " + str(neighbor_variance))
    if initial_params is not None:
        if len(initial_params) != param_count:
            raise ValueError("initial_params has wrong length: " + str(len(initial_params)))
        if min(initial_params) <= 0.0 or max(initial_params) >= 1.0:
            raise ValueError("initial_params has invalid value: " + str(initial_params))

    start_time = time.time()
    if initial_params is None:
        saved_params = [random.random() for _ in range(param_count)]
    else:
        saved_params = initial_params.copy()
    saved_value = None
    best_params = saved_params.copy()
    best_value = None
    fmt = "{0:.4f}"
    for time_step in range(max_steps):
        cur_temp = get_temp(time_step, max_steps, max_temp)
        cur_neighbor = [neighbor_truncated_gaussian(x, \
            neighbor_variance) for x in saved_params]
        cur_neighbor = [float(fmt.format(cur_neighbor[0]))]
        cur_mean_result = sample_function(cur_neighbor, att_mixed_strat)
        if best_value is None or cur_mean_result > best_value:
            best_params = cur_neighbor.copy()
            best_value = cur_mean_result
        if should_switch_params(saved_value, cur_mean_result, cur_temp):
            saved_params = cur_neighbor.copy()
            saved_value = cur_mean_result
            if should_print:
                print("Updated params at anneal step " + str(time_step) + ": " + \
                    str([fmt.format(x) for x in saved_params]) + ", value: " + \
                    fmt.format(saved_value), flush=True)
    duration = time.time() - start_time
    print("Best params: " + str([fmt.format(x) for x in best_params]), flush=True)
    print("Best mean value: " + fmt.format(best_value), flush=True)
    print("Seconds used for simulated annealing: " + str(int(duration)), flush=True)
    return best_params, best_value
