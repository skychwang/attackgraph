import sys
import datetime
import subprocess
import os
import os.path
from add_new_data import get_add_data_result_file_name
import check_if_beneficial as check
from train_test_def import wait_for_def_lock, lock_def, read_def_port, \
    is_def_unlocked, PORTS_PER_ROUND, MAX_PORT, MIN_PORT
from train_test_att import is_att_unlocked
from train_test_both import run_both

def are_all_locks_unlocked(port_lock_name):
    return is_def_unlocked(port_lock_name, True) and \
        is_def_unlocked(port_lock_name, False) and \
        is_att_unlocked(port_lock_name, True) and \
        is_att_unlocked(port_lock_name, False)

def check_for_files(game_number, env_short_name_payoffs):
    dirs = ["depgraphpy4jattvseither", "depgraphpy4jdefvseither", "depgraphpy4jboth", \
            "dg4jattcli", "dg4jdefcli", "dg4jnonetcli", "graphs", "simspecs"]
    files = ["defaults.json",
             "game_" + str(game_number) + ".json",
             "attNetStrings_" + str(env_short_name_payoffs) + ".txt",
             "defNetStrings_" + str(env_short_name_payoffs) + ".txt",
             "attStratStrings_" + str(env_short_name_payoffs) + ".txt",
             "defStratStrings_" + str(env_short_name_payoffs) + ".txt",
             "oldAttNetNames_" + str(env_short_name_payoffs) + ".txt",
             "oldDefNetNames_" + str(env_short_name_payoffs) + ".txt"
            ]
    for cur_dir in dirs:
        if not os.path.isdir(cur_dir):
            raise ValueError("Missing directory: " + cur_dir)
    for cur_file in files:
        if not os.path.exists(cur_file):
            raise ValueError("Missing file: " + cur_file)

def run_gambit(game_number, cur_epoch, env_short_name_payoffs):
    cmd_list = ["python3", "gambit_analyze.py", str(game_number), str(cur_epoch), \
        env_short_name_payoffs]
    subprocess.call(cmd_list)

def run_create_tsv(game_number, cur_epoch, env_short_name_tsv, env_short_name_payoffs):
    cmd_list = ["python3", "create_tsv_files.py", str(game_number), str(cur_epoch), \
        env_short_name_tsv, env_short_name_payoffs]
    subprocess.call(cmd_list)

def run_update_strats(env_short_name_tsv, port_lock_name, new_epoch):
    cmd_list = ["python3", "update_opponent_strats.py", port_lock_name, \
        env_short_name_tsv, str(new_epoch)]
    subprocess.call(cmd_list)

def run_gen_both_payoffs(game_number, env_short_name_payoffs, new_epoch):
    cmd_list = ["python3", "get_both_payoffs_from_game.py", str(game_number), \
        env_short_name_payoffs, str(new_epoch)]
    subprocess.call(cmd_list)

def run_train_test_both(graph_name, env_short_name_payoffs, new_epoch, \
    env_name_vs_mixed_att, env_name_vs_mixed_def, port_lock_name, env_short_name_tsv, \
    max_timesteps_def, max_timesteps_att):
    is_train = True
    wait_for_def_lock(port_lock_name, is_train)
    lock_def(port_lock_name, is_train)
    def_port = read_def_port(port_lock_name, is_train)
    def_port += PORTS_PER_ROUND
    if def_port >= MAX_PORT:
        def_port = MIN_PORT

    run_both(graph_name, env_short_name_payoffs, new_epoch, env_name_vs_mixed_att, \
        env_name_vs_mixed_def, port_lock_name, def_port, env_short_name_tsv, \
        max_timesteps_def, max_timesteps_att)

def run_train_test_all(graph_name, env_short_name_payoffs, new_epoch, \
    env_name_vs_mixed_att, env_name_vs_mixed_def, port_lock_name, env_short_name_tsv, \
    max_timesteps_def, max_timesteps_att):
    is_train = True
    wait_for_def_lock(port_lock_name, is_train)
    lock_def(port_lock_name, is_train)
    def_port = read_def_port(port_lock_name, is_train)
    def_port += PORTS_PER_ROUND
    if def_port >= MAX_PORT:
        def_port = MIN_PORT

    cmd_list_train_def = ["python3", "train_test_def.py", graph_name, \
        env_short_name_payoffs, str(new_epoch), env_name_vs_mixed_att, port_lock_name, \
        str(def_port), env_short_name_tsv, str(max_timesteps_def)]
    cmd_list_train_att = ["python3", "train_test_att.py", graph_name, \
        env_short_name_payoffs, str(new_epoch), env_name_vs_mixed_def, str(def_port), \
        port_lock_name, env_short_name_tsv, str(max_timesteps_att)]
    process_train_def = subprocess.Popen(cmd_list_train_def, stdin=None, stdout=None, \
        stderr=None, close_fds=True)
    process_train_att = subprocess.Popen(cmd_list_train_att, stdin=None, stdout=None, \
        stderr=None, close_fds=True)

    process_train_def.wait()
    process_train_att.wait()

def get_check_if_beneficial(env_short_name_payoffs, new_epoch, is_def):
    return check.check_for_cli(env_short_name_payoffs, new_epoch, is_def)

def run_gen_new_cols(env_name_def_net, env_name_att_net, env_name_both, new_col_count, \
    new_epoch, env_short_name_payoffs, is_def_beneficial, is_att_beneficial, graph_name):
    cmd_list = ["python3", "generate_new_cols.py", env_name_def_net, env_name_att_net, \
        env_name_both, str(new_col_count), str(new_epoch), env_short_name_payoffs, \
        str(is_def_beneficial), str(is_att_beneficial), graph_name]
    subprocess.call(cmd_list)

def run_append_net_names(env_short_name_payoffs, new_epoch, def_pkl_prefix, \
    att_pkl_prefix, is_def_beneficial, is_att_beneficial):
    cmd_list = ["python3", "append_net_names.py", env_short_name_payoffs, str(new_epoch), \
        def_pkl_prefix, att_pkl_prefix, str(is_def_beneficial), \
        str(is_att_beneficial)]
    subprocess.call(cmd_list)

def run_add_new_data(game_number, env_short_name_payoffs, new_epoch):
    cmd_list = ["python3", "add_new_data.py", str(game_number), env_short_name_payoffs, \
        str(new_epoch)]
    subprocess.call(cmd_list)

def run_epoch(game_number, cur_epoch, env_short_name_tsv, env_short_name_payoffs, \
    env_name_def_net, env_name_att_net, env_name_both, graph_name, \
    env_name_vs_mixed_def, env_name_vs_mixed_att, new_col_count, def_pkl_prefix, \
    att_pkl_prefix, port_lock_name, max_timesteps_def, max_timesteps_att):
    new_epoch = cur_epoch + 1
    result_file_name = get_add_data_result_file_name(game_number, new_epoch, \
        env_short_name_payoffs)
    if os.path.isfile(result_file_name):
        raise ValueError("Cannot run epoch " + str(cur_epoch) + ": " + \
            result_file_name + " already exists.")

    print("\tWill run gambit, epoch: " + str(cur_epoch)+ ", time: " + \
        str(datetime.datetime.now()), flush=True)
    # get Nash equilibrium of current strategies
    run_gambit(game_number, cur_epoch, env_short_name_payoffs)
    # create TSV file for current attacker and defender equilibrium strategies
    run_create_tsv(game_number, cur_epoch, env_short_name_tsv, env_short_name_payoffs)

    # set the mixed-strategy opponents to use current TSV file strategies
    run_update_strats(env_short_name_tsv, port_lock_name, new_epoch)
    print("\tWill get att and def payoffs, epoch: " + str(new_epoch)+ ", time: " + \
        str(datetime.datetime.now()), flush=True)
    # sample and estimate mean payoff of each defender strategy vs. new equilibrium
    # sample and estimate mean payoff of each attacker strategy vs. new equilibrium
    run_gen_both_payoffs(game_number, env_short_name_payoffs, new_epoch)
    print("\tWill train and test both, epoch: " + str(new_epoch)+ ", time: " + \
        str(datetime.datetime.now()), flush=True)
    # train defender network against current attacker equilibrium, and sample its payoff
    # train attacker network against current defender equilibrium, and sample its payoff
    run_train_test_both(graph_name, env_short_name_payoffs, new_epoch, \
        env_name_vs_mixed_att, env_name_vs_mixed_def, port_lock_name, env_short_name_tsv, \
        max_timesteps_def, max_timesteps_att)

    # check if new defender network is beneficial deviation from old equilibrium
    is_def_beneficial = get_check_if_beneficial(env_short_name_payoffs, new_epoch, True)
    # check if new attacker network is beneficial deviation from old equilibrium
    is_att_beneficial = get_check_if_beneficial(env_short_name_payoffs, new_epoch, False)

    if not is_def_beneficial and not is_att_beneficial:
        # neither network beneficially deviates, so stop
        print("\tConverged after round: " + str(new_epoch), flush=True)
        return False

    print("\tWill generate new columns, epoch: " + str(new_epoch)+ ", time: " + \
        str(datetime.datetime.now()), flush=True)
    # sample payoff of each new network (if it beneficially deviates) against all opponent
    # strategies
    run_gen_new_cols(env_name_def_net, env_name_att_net, env_name_both, new_col_count, \
        new_epoch, env_short_name_payoffs, is_def_beneficial, is_att_beneficial, \
        graph_name)
    # append name of each new network (if it beneficially deviates) to list of networks to
    # use in equilibrium search
    run_append_net_names(env_short_name_payoffs, new_epoch, def_pkl_prefix, \
        att_pkl_prefix, is_def_beneficial, is_att_beneficial)

    # add new payoff data to game object (from new beneficially deviating network(s))
    run_add_new_data(game_number, env_short_name_payoffs, new_epoch)

    print("\tShould continue after round: " + str(new_epoch), flush=True)
    return True

def main(game_number, cur_epoch, env_short_name_tsv, env_short_name_payoffs, \
        env_name_def_net, env_name_att_net, env_name_both, graph_name, \
        env_name_vs_mixed_def, env_name_vs_mixed_att, new_col_count, def_pkl_prefix, \
        att_pkl_prefix, port_lock_name, max_timesteps_def, max_timesteps_att, \
        max_new_rounds):
    check_for_files(game_number, env_short_name_payoffs)
    if not are_all_locks_unlocked(port_lock_name):
        raise ValueError("Lock is being held: " + port_lock_name)
    should_continue = True
    my_epoch = cur_epoch
    print("\tStarting from epoch: " + str(my_epoch), flush=True)
    rounds_left = max_new_rounds
    while should_continue and (rounds_left is None or rounds_left > 0):
        print("\tWill run epoch: " + str(my_epoch) + ", time: " + \
            str(datetime.datetime.now()), flush=True)
        should_continue = run_epoch(game_number, my_epoch, env_short_name_tsv, \
            env_short_name_payoffs, env_name_def_net, env_name_att_net, env_name_both, \
            graph_name, env_name_vs_mixed_def, env_name_vs_mixed_att, new_col_count, \
            def_pkl_prefix, att_pkl_prefix, port_lock_name, max_timesteps_def, \
            max_timesteps_att)
        if should_continue:
            my_epoch += 1
            if rounds_left is not None:
                rounds_left -= 1
    if not should_continue:
        print("\tConverged at epoch: " + str(my_epoch) + ", time: " + \
            str(datetime.datetime.now()), flush=True)
    else:
        print("\tRan max_new_rounds by epoch: " + str(my_epoch) + ", time: " + \
            str(datetime.datetime.now()), flush=True)

'''
example: python3 master_dq_runner.py 3013 17 sl29_randNoAndB sl29 DepgraphJava29N-v0 \
    DepgraphJavaEnvAtt29N-v0 DepgraphJavaEnvBoth29N-v0 \
    SepLayerGraph0_noAnd_B.json DepgraphJavaEnvVsMixedDef29N-v0 \
    DepgraphJavaEnvVsMixedAtt29N-v0 400 dg_sl29_dq_mlp_rand_epoch dg_sl29_dq_mlp_rand_epoch
    s29 700000 700000 None
'''
if __name__ == '__main__':
    if len(sys.argv) != 18:
        raise ValueError("Need 17 args: game_number, cur_epoch, env_short_name_tsv, " + \
            "env_short_name_payoffs, env_name_def_net, env_name_att_net, " + \
            "env_name_both, graph_name, env_name_vs_mixed_def, " + \
            "env_name_vs_mixed_att, new_col_count, def_pkl_prefix, att_pkl_prefix, " + \
            "port_lock_name, max_timesteps_def, max_timesteps_att, max_new_rounds")
    GAME_NUMBER = int(sys.argv[1])
    CUR_EPOCH = int(sys.argv[2])
    ENV_SHORT_NAME_TSV = sys.argv[3]
    ENV_SHORT_NAME_PAYOFFS = sys.argv[4]
    ENV_NAME_DEF_NET = sys.argv[5]
    ENV_NAME_ATT_NET = sys.argv[6]
    ENV_NAME_BOTH = sys.argv[7]
    GRAPH_NAME = sys.argv[8]
    ENV_NAME_VS_MIXED_DEF = sys.argv[9]
    ENV_NAME_VS_MIXED_ATT = sys.argv[10]
    NEW_COL_COUNT = int(sys.argv[11])
    DEF_PKL_PREFIX = sys.argv[12]
    ATT_PKL_PREFIX = sys.argv[13]
    PORT_LOCK_NAME = sys.argv[14]
    MAX_TIMESTEPS_DEF = int(sys.argv[15])
    MAX_TIMESTEPS_ATT = int(sys.argv[16])
    MAX_NEW_ROUNDS = sys.argv[17]
    if MAX_NEW_ROUNDS == "None":
        MAX_NEW_ROUNDS = None
    else:
        MAX_NEW_ROUNDS = int(MAX_NEW_ROUNDS)
    main(GAME_NUMBER, CUR_EPOCH, ENV_SHORT_NAME_TSV, ENV_SHORT_NAME_PAYOFFS, \
        ENV_NAME_DEF_NET, ENV_NAME_ATT_NET, ENV_NAME_BOTH, GRAPH_NAME, \
        ENV_NAME_VS_MIXED_DEF, ENV_NAME_VS_MIXED_ATT, NEW_COL_COUNT, \
        DEF_PKL_PREFIX, ATT_PKL_PREFIX, PORT_LOCK_NAME, MAX_TIMESTEPS_DEF, \
        MAX_TIMESTEPS_ATT, MAX_NEW_ROUNDS)
