import pandas as pd
import os

if __name__ == "__main__":
    file_list = os.listdir('./WIFIengine (2)/train/suwon/train_1/')

    save_df = pd.trainFrame([])

    for file in file_list:
        if(file != 'total.txt' and file != 'total' and file != '추가할것'):
            df = pd.read_csv('./WIFIengine (2)/data/suwon/train_1/' + file,
            sep = "\t", engine = 'python', encoding = "cp949", header = None)
            save_df = pd.concat([save_df, df])

    save_df.to_csv('./WIFIengine (2)/data/suwon/total/total.txt', index=False, header=None, sep="\t")