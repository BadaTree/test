import pandas as pd
import matplotlib.pyplot as plt

if __name__ == "__main__":
    #x,y 좌표를 확인하고 싶은 파일 입력
    filename = 'total'

    ##############################

    filename += '.txt'
    x_y_data = pd.read_csv(
        './data/'+filename,
        sep = "\t", engine = 'python', encoding = "utf-8", header = None)

    x_coord = x_y_data.iloc[:, 0]
    y_coord = x_y_data.iloc[:, 1]

    figure, axes = plt.subplots()
    plt.scatter(x_coord, y_coord, color ='g',s = 5)
    # axes.set_aspect(1)
    plt.show()