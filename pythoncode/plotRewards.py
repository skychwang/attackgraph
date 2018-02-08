
import os
import matplotlib.pyplot as plt

def getLines(fileName):
    lines = None
    with open(fileName) as f:
        lines = f.readlines()
    return lines

def getRewardsList(lines):
    return getValuesList(lines, "episode reward")

def getEpisodesList(lines):
    return getValuesList(lines, "episodes")

def getValuesList(lines, stringIndicator):
    result = []
    for line in lines:
        if stringIndicator in line:
            firstBarIndex = line.find("|")
            secondBarIndex = line.find("|", firstBarIndex + 1)
            thirdBarIndex = line.find("|", secondBarIndex + 1)
            innerPart = line[secondBarIndex + 1:thirdBarIndex].strip()
            floatValue = float(innerPart)
            result.append(floatValue)
    return result

def myPlot(episodes, rewards, goal_reward, own_reward, save_name):
    fig, ax = plt.subplots()
    my_lw = 3
    plt.plot(episodes, rewards, lw=my_lw, c='blue', label="QL exploring")

    plt.axhline(y=own_reward, lw=my_lw, c='blue', linestyle='--', label="QL final")
    plt.axhline(y=goal_reward, lw=my_lw, c='red', linestyle='-.', label="Best heuristic")

    plt.legend(loc=4) # lower-right

    labelSize = 20
    plt.xlabel("Episode", fontsize=labelSize)
    plt.ylabel("Defender\nreward", fontsize=labelSize)
    plt.tight_layout()
    #plt.show()
    plt.savefig(save_name)

def main():
    base_name = "tdj_conv3"
    file_name = base_name + ".txt"
    save_name = base_name + ".pdf"
    lines = getLines(file_name)
    episodes_list = getEpisodesList(lines)
    rewards_list = getRewardsList(lines)
    # print(rewards_list)

    goal_reward = -26.9
    own_reward = -21.6
    myPlot(episodes_list, rewards_list, goal_reward, own_reward, save_name)

if __name__ == "__main__":
    main()