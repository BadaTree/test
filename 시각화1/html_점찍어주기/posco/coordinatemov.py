from selenium import webdriver
import chromedriver_autoinstaller
import time

path = chromedriver_autoinstaller.install()
browser = webdriver.Chrome(path)
browser.implicitly_wait(3)
browser.get("C:///Github/final_interpolation/시각화/html_file/posco_pohang2.html")

f = open(f"./posco_randompath.txt")
lines = f.readlines()
time.sleep(10)

for line in lines:
    row = list(map(float, line.split()))
    x1 = row[0]
    y1 = row[1]

    # x2 = row[2]
    # y2 = row[3]
    browser.execute_script(f"androidBridge({x1}, {y1})\n")
    # time.sleep(0.1)

# 1번, 5번, 7번이 복도에서 시작