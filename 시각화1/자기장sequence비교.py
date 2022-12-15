import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

if __name__ == "__main__":
    fileroute = 'C:/Users/skhwa/Desktop/backup/rawdatas/포스코/자기장검증data/total/자기장비교/'

    dforiginal = pd.read_csv(fileroute + 'extendmagz.txt',
            sep = "\t", engine = 'python', encoding = "cp949", header = None)

    dfnew = pd.read_csv(fileroute + 'newmagz.txt',
                             sep="\t", engine='python', encoding="cp949", header=None)

    dforiginal = dforiginal.replace(0, np.NaN)
    dfnew = dfnew.replace(0, np.NaN)

    dforiginal = dforiginal.iloc[168:175, 0:391]
    dfnew = dfnew.iloc[168:175, 0:391]

    for i in range(dforiginal.shape[0]):
        fun1 = dforiginal.iloc[i, 0:-1]
        fun2 = dfnew.iloc[i, 0:-1]
        fun1 = fun1 - fun1.mean()
        fun2 = fun2 - fun2.mean()
        figure, axes = plt.subplots()
        axes.set_aspect(1)
        plt.axis('on')
        plt.grid('True')
        plt.plot(fun1)
        plt.plot(fun2)
        plt.show()