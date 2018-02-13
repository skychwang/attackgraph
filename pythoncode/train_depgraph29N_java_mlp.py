'''
Trains a multilayer perceptron to play depgraph against an attacker agent.
'''
import time
import gym

from baselines import deepq

def main():
    '''
    Makes the depgraph environment, builds a multilayer perceptron model,
    trains the model, and saves the result.
    '''
    env_name = "DepgraphJava29N-v0"
    print("Environment: " + env_name)

    start = time.time()
    env = gym.make(env_name)
    model = deepq.models.mlp([256, 256])
    act = deepq.learn(
        env,
        q_func=model,
        lr=1e-5,
        max_timesteps=2000000,
        buffer_size=50000,
        exploration_fraction=0.5,
        exploration_final_eps=0.03,
        checkpoint_freq=50000,
        print_freq=1000,
        param_noise=False,
        gamma=0.99,
        ep_mean_length=1000
    )
    model_name = "depgraph_SepLayer0_deepqconsrv_mlp_long.pkl"
    print("Saving model to: " + model_name)
    act.save(model_name)
    end = time.time()
    elapsed = end - start
    minutes = elapsed // 60
    print("Minutes taken: " + str(minutes))
    print("Opponent was: " + env_name)

if __name__ == '__main__':
    main()
